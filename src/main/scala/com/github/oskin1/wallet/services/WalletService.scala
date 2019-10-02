package com.github.oskin1.wallet.services

import canoe.models.ChatId
import cats.{Applicative, MonadError}
import cats.data.NonEmptyList
import cats.effect.Sync
import cats.implicits._
import com.github.oskin1.wallet.models.network.{Balance, Output}
import com.github.oskin1.wallet.models.storage.{Account, Wallet}
import com.github.oskin1.wallet.models.{
  storage,
  NewWallet,
  RestoredWallet,
  TransactionRequest
}
import com.github.oskin1.wallet.repos.WalletRepo
import com.github.oskin1.wallet.Settings
import com.github.oskin1.wallet.crypto.encryption
import org.ergoplatform.wallet.interpreter.ErgoUnsafeProver
import org.ergoplatform.wallet.mnemonic.Mnemonic
import org.ergoplatform.wallet.secrets.{
  ExtendedSecretKey,
  ExtendedSecretKeySerializer
}
import org.ergoplatform.{
  ErgoAddressEncoder,
  ErgoBoxCandidate,
  ErgoLikeTransaction,
  P2PKAddress,
  UnsignedErgoLikeTransaction
}

import scala.util.Try

/** Provides actual wallet functionality.
  */
trait WalletService[F[_]] {

  def restoreWallet(
    chatId: ChatId.Chat,
    mnemonic: String,
    pass: String,
    mnemonicPassOpt: Option[String]
  ): F[RestoredWallet]

  def createWallet(
    chatId: ChatId.Chat,
    pass: String,
    mnemonicPassOpt: Option[String],
  ): F[NewWallet]

  def createTransaction(
    chatId: ChatId.Chat,
    pass: String,
    requests: List[TransactionRequest],
    fee: Long
  ): F[String]

  def getBalance(chatId: ChatId.Chat): F[Balance]
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
      chatId: ChatId.Chat,
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
      chatId: ChatId.Chat,
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
      chatId: ChatId.Chat,
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
                    .map(collectOutputs(_, requests.map(_.amount).sum + fee))
                    .map(
                      _.map(
                        _ -> rootSk
                          .derive(account.derivationPath)
                          .asInstanceOf[ExtendedSecretKey]
                      )
                    )
                }
                .sequence
                .flatMap { x =>
                  explorerService.getCurrentHeight.flatMap { height =>
                    makeTransaction(x.toList.flatten, requests, fee, height)
                      .fold[F[ErgoLikeTransaction]](
                        e =>
                          MonadError[F, Throwable].raiseError(
                            new Exception(
                              s"Transaction assembly error: ${e.getMessage}"
                            )
                          ),
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

    def getBalance(chatId: ChatId.Chat): F[Balance] =
      walletRepo.readWallet(chatId).flatMap {
        _.fold(Applicative[F].pure(Balance.empty)) { wallet =>
          wallet.accounts
            .map(x => explorerService.getBalance(x.rawAddress))
            .sequence
            .map(_.reduce[Balance](_ merge _))
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

    private def collectOutputs(
      outputs: List[Output],
      requiredAmount: Long
    ): List[Output] = {
      @scala.annotation.tailrec
      def loop(
        acc: List[Output],
        rem: List[Output],
        amtRem: Long
      ): List[Output] =
        rem match {
          case head :: tail if amtRem > 0 =>
            loop(acc :+ head, tail, amtRem - head.value)
          case _ =>
            acc
        }
      loop(List.empty, outputs, requiredAmount)
    }

    private def makeTransaction(
      inputs: List[(Output, ExtendedSecretKey)],
      requests: List[TransactionRequest],
      fee: Long,
      currentHeight: Int
    ): Try[ErgoLikeTransaction] = ???
  }
}
