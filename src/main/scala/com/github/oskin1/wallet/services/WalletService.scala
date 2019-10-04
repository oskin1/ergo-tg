package com.github.oskin1.wallet.services

import cats.effect.{Async, Sync}
import cats.implicits._
import cats.{Applicative, MonadError}
import com.github.oskin1.wallet.models.network.Balance
import com.github.oskin1.wallet.models.storage.Wallet
import com.github.oskin1.wallet.models.{
  NewWallet,
  RestoredWallet,
  TransactionRequest
}
import com.github.oskin1.wallet.modules.{
  SecretManagement,
  TransactionManagement
}
import com.github.oskin1.wallet.repositories.WalletRepo
import com.github.oskin1.wallet.storage.LDBStorage
import com.github.oskin1.wallet.{repositories, services, Settings}
import org.ergoplatform._
import org.ergoplatform.wallet.secrets.ExtendedSecretKey
import org.http4s.client.Client
import org.iq80.leveldb.DB

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
  ) extends WalletService[F]
    with TransactionManagement[F]
    with SecretManagement[F] {

    implicit private val addressEncoder: ErgoAddressEncoder =
      ErgoAddressEncoder(settings.addressPrefix.toByte)

    def restoreWallet(
      chatId: Long,
      mnemonic: String,
      pass: String,
      mnemonicPassOpt: Option[String],
    ): F[RestoredWallet] = {
      val wallet = Wallet.rootWallet(mnemonic, pass, mnemonicPassOpt)(settings)
      walletRepo.putWallet(chatId, wallet) *> Sync[F].delay(
        RestoredWallet(wallet.accounts.head.rawAddress)
      )
    }

    def createWallet(
      chatId: Long,
      pass: String,
      mnemonicPassOpt: Option[String]
    ): F[NewWallet] =
      generateMnemonic(settings)
        .flatMap { mnemonic =>
          val wallet =
            Wallet.rootWallet(mnemonic, pass, mnemonicPassOpt)(settings)
          walletRepo.putWallet(chatId, wallet) *> Sync[F].delay(
            NewWallet(wallet.accounts.head.rawAddress, mnemonic)
          )
        }

    def createTransaction(
      chatId: Long,
      pass: String,
      requests: List[TransactionRequest],
      fee: Long
    ): F[String] =
      walletRepo.readWallet(chatId).flatMap {
        case Some(wallet) =>
          decryptSecret(wallet.secret, pass)
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
                .flatMap { outputs =>
                  collectOutputs(outputs.toList.flatten, requests, fee)
                }
                .flatMap { inputs =>
                  explorerService.getBlockchainInfo.flatMap { info =>
                    makeTransaction(inputs, requests, fee, info.height)
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
  }

  object Live {

    /** Production wallet service smart constructor.
      */
    def apply[F[_]: Async](
      db: DB,
      client: Client[F],
      settings: Settings
    ): Live[F] = {
      val explorerService =
        new services.ExplorerService.Live[F](client, settings)
      val storage = new LDBStorage[F](db)
      val walletRepo = new repositories.WalletRepo.Live[F](storage)
      new Live[F](explorerService, walletRepo, settings)
    }
  }
}
