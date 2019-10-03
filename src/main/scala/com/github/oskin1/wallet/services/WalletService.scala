package com.github.oskin1.wallet.services

import canoe.models.ChatId
import cats.data.NonEmptyList
import cats.effect.Sync
import cats.implicits._
import cats.{Applicative, MonadError}
import com.github.oskin1.wallet.Settings
import com.github.oskin1.wallet.crypto.{encryption, UnsafeMultiProver}
import com.github.oskin1.wallet.models.network.{Balance, Output}
import com.github.oskin1.wallet.models.storage.{Account, Wallet}
import com.github.oskin1.wallet.models.{
  storage,
  NewWallet,
  RestoredWallet,
  TransactionRequest
}
import com.github.oskin1.wallet.repos.WalletRepo
import org.ergoplatform.wallet.mnemonic.Mnemonic
import org.ergoplatform.wallet.secrets.{
  DerivationPath,
  ExtendedSecretKey,
  ExtendedSecretKeySerializer
}
import org.ergoplatform._
import scorex.crypto.authds.ADKey
import scorex.util.encode.Base16

import scala.util.{Failure, Success, Try}

/** Provides actual wallet functionality.
  */
trait WalletService[F[_]] {

  /** Restore from existing mnemonic phrase, associate
    * it with a given chatId and persist it.
    */
  def restoreWallet(
    chatId: Long,
    mnemonic: String,
    pass: String,
    mnemonicPassOpt: Option[String] = None
  ): F[RestoredWallet]

  /** Create new wallet, associate it with a given
    * chatId and persist it.
    */
  def createWallet(
    chatId: Long,
    pass: String,
    mnemonicPassOpt: Option[String] = None
  ): F[NewWallet]

  /** Create new transaction and submit it to the network.
    */
  def createTransaction(
    chatId: Long,
    pass: String,
    requests: List[TransactionRequest],
    fee: Long
  ): F[String]

  /** Get an aggregated balance for a given chatId from the network.
    */
  def getBalance(chatId: Long): F[Option[Balance]]
}

object WalletService {

  final class Live[F[_]: Sync](
    explorerService: ExplorerService[F],
    walletRepo: WalletRepo[F],
    settings: Settings
  ) extends WalletService[F] {

    implicit private val addressEncoder: ErgoAddressEncoder =
      ErgoAddressEncoder(settings.addressPrefix)

    def restoreWallet(
      chatId: Long,
      mnemonic: String,
      pass: String,
      mnemonicPassOpt: Option[String],
    ): F[RestoredWallet] = {
      val wallet = deriveRootWallet(mnemonic, pass, mnemonicPassOpt)
      walletRepo.putWallet(chatId, wallet) *> Sync[F].delay(
        RestoredWallet(wallet.accounts.head.rawAddress)
      )
    }

    def createWallet(
      chatId: Long,
      pass: String,
      mnemonicPassOpt: Option[String]
    ): F[NewWallet] = {
      val entropy =
        scorex.utils.Random.randomBytes(settings.seedStrengthBits / 8)
      new Mnemonic(settings.mnemonicPhraseLanguage, settings.seedStrengthBits)
        .toMnemonic(entropy)
        .map { mnemonic =>
          val wallet = deriveRootWallet(mnemonic, pass, mnemonicPassOpt)
          walletRepo.putWallet(chatId, wallet) *> Sync[F].delay(
            NewWallet(wallet.accounts.head.rawAddress, mnemonic)
          )
        }
        .fold(e => MonadError[F, Throwable].raiseError(e), r => r)
    }

    def createTransaction(
      chatId: Long,
      pass: String,
      requests: List[TransactionRequest],
      fee: Long
    ): F[String] =
      walletRepo.readWallet(chatId).flatMap {
        case Some(wallet) =>
          encryption
            .decrypt(wallet.secret, pass)
            .fold[F[Array[Byte]]](
              e =>
                MonadError[F, Throwable].raiseError(
                  new Exception(s"Incorrect pass: ${e.getMessage}")
              ),
              r => Applicative[F].pure(r)
            )
            .flatMap { seed =>
              val rootSk = ExtendedSecretKey.deriveMasterKey(seed)
              wallet.accounts
                .map { account =>
                  explorerService
                    .getUnspentOutputs(account.rawAddress)
                    .map {
                      _.map(_ -> deriveKey(rootSk, account.derivationPath))
                    }
                }
                .sequence
                .flatMap { x =>
                  collectOutputs(x.toList.flatten, requests, fee)
                    .fold[F[List[(Output, ExtendedSecretKey)]]](
                      e => MonadError[F, Throwable].raiseError(e),
                      r => Applicative[F].pure(r)
                    )
                }
                .flatMap { inputs =>
                  explorerService.getBlockchainInfo.flatMap { info =>
                    makeTransaction(inputs, requests, fee, info.height)
                      .fold[F[ErgoLikeTransaction]](
                        e => MonadError[F, Throwable].raiseError(e),
                        r => Applicative[F].pure(r)
                      )
                      .flatMap(explorerService.submitTransaction)
                  }
                }
            }
        case None =>
          MonadError[F, Throwable]
            .raiseError(new Exception(s"Wallet with id $chatId not found"))
      }

    def getBalance(chatId: Long): F[Option[Balance]] =
      walletRepo.readWallet(chatId).flatMap {
        _.fold[F[Option[Balance]]](Applicative[F].pure(None)) { wallet =>
          wallet.accounts
            .map(x => explorerService.getBalance(x.rawAddress))
            .sequence
            .map(x => Some(x.reduce[Balance](_ merge _)))
        }
      }

    private def deriveRootWallet(
      mnemonic: String,
      pass: String,
      mnemonicPassOpt: Option[String]
    ): Wallet = {
      val seed = Mnemonic.toSeed(mnemonic, mnemonicPassOpt)
      val secret = ExtendedSecretKey.deriveMasterKey(seed)
      val encryptedSecret =
        encryption.encrypt(ExtendedSecretKeySerializer.toBytes(secret), pass)(
          settings.encryption
        )
      val rootAddress = P2PKAddress(secret.publicKey.key)
      storage.Wallet(
        encryptedSecret,
        NonEmptyList(Account(rootAddress.toString(), secret.path), List.empty)
      )
    }

    private def deriveKey(
      rootSk: ExtendedSecretKey,
      derivationPath: DerivationPath
    ): ExtendedSecretKey =
      rootSk.derive(derivationPath).asInstanceOf[ExtendedSecretKey]

    private def collectOutputs(
      outputs: List[(Output, ExtendedSecretKey)],
      requests: List[TransactionRequest],
      fee: Long
    ): Try[List[(Output, ExtendedSecretKey)]] = {
      @scala.annotation.tailrec
      def loop(
        acc: List[(Output, ExtendedSecretKey)],
        rem: List[(Output, ExtendedSecretKey)],
        amtRem: Long
      ): Try[List[(Output, ExtendedSecretKey)]] =
        rem match {
          case head :: tail if amtRem > 0 =>
            loop(acc :+ head, tail, amtRem - head._1.value)
          case _ if amtRem <= 0 =>
            Success(acc)
          case _ =>
            Failure(new Exception("Not enough boxes"))
        }
      loop(List.empty, outputs, requests.map(_.amount).sum + fee)
    }

    private def makeTransaction(
      inputs: List[(Output, ExtendedSecretKey)],
      requests: List[TransactionRequest],
      fee: Long,
      currentHeight: Int
    ): Try[ErgoLikeTransaction] =
      inputs
        .map {
          case (out, sk) =>
            Base16.decode(out.id).map(x => (ADKey @@ x, sk.key))
        }
        .sequence
        .map { decodedInputs =>
          val unsignedInputs =
            decodedInputs.map(x => new UnsignedInput(x._1)).toIndexedSeq
          val feeOutput = new ErgoBoxCandidate(
            fee,
            ErgoScriptPredef.feeProposition(),
            currentHeight
          )
          val outputs = requests.map { req =>
            new ErgoBoxCandidate(req.amount, req.address.script, currentHeight)
          }.toIndexedSeq
          val unsignedTx = new UnsignedErgoLikeTransaction(
            unsignedInputs,
            IndexedSeq.empty,
            outputs :+ feeOutput
          )
          UnsafeMultiProver.prove(unsignedTx, decodedInputs)
        }
  }
}
