package com.github.oskin1.wallet.services

import cats.MonadError
import cats.effect.concurrent.Ref
import cats.effect.{Async, Sync}
import cats.implicits._
import com.github.oskin1.wallet.WalletError.WalletNotFound
import com.github.oskin1.wallet.models.network.Balance
import com.github.oskin1.wallet.models.storage.Wallet
import com.github.oskin1.wallet.models.{NewWallet, PaymentRequest, RestoredWallet}
import com.github.oskin1.wallet.modules.{SecretManagement, TransactionManagement}
import com.github.oskin1.wallet.persistence.{LDBStorage, UtxPool}
import com.github.oskin1.wallet.repositories.WalletRepo
import com.github.oskin1.wallet.settings.Settings
import com.github.oskin1.wallet.{repositories, ModifierId}
import org.ergoplatform._
import org.ergoplatform.wallet.secrets.ExtendedSecretKey
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

  /** Delete a wallet associated with a given chatId.
    */
  def deleteWallet(
    chatId: Long,
    pass: String
  ): F[Unit]

  /** Create new transaction and submit it to the network.
    */
  def createTransaction(
    chatId: Long,
    pass: String,
    requests: List[PaymentRequest],
    fee: Long
  ): F[String]

  /** Get an aggregated balance for a given chatId from the network.
    */
  def getBalance(chatId: Long): F[Balance]

  /** Check whether a wallet associated with a given `chatId` exists.
    */
  def exists(chatId: Long): F[Boolean]
}

object WalletService {

  final class Live[F[_]: Sync](
    utxPoolRef: Ref[F, UtxPool],
    explorerService: ExplorerService[F],
    walletRepo: WalletRepo[F],
    settings: Settings
  ) extends WalletService[F]
    with TransactionManagement[F]
    with SecretManagement[F] {

    implicit private val addressEncoder: ErgoAddressEncoder =
      settings.addressEncoder

    def restoreWallet(
      chatId: Long,
      mnemonic: String,
      pass: String,
      mnemonicPassOpt: Option[String],
    ): F[RestoredWallet] = {
      val wallet =
        Wallet.rootWallet(mnemonic, pass, mnemonicPassOpt)(settings)
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

    def deleteWallet(
      chatId: Long,
      pass: String
    ): F[Unit] =
      withWallet(chatId) { wallet =>
        decryptSecret(wallet.secret, pass).flatMap { _ =>
          walletRepo.deleteWallet(chatId)
        }
      }

    def createTransaction(
      chatId: Long,
      pass: String,
      requests: List[PaymentRequest],
      fee: Long
    ): F[String] =
      withWallet(chatId) { wallet =>
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
                  addressEncoder
                    .fromString(wallet.changeAddress)
                    .fold(
                      e => MonadError[F, Throwable].raiseError(e),
                      changeAddr =>
                        makeTransaction(inputs, requests, fee, info.height, changeAddr)
                          .flatMap(explorerService.submitTransaction)
                          .flatMap(id => addToUtxPool(id, chatId).map(_ => id))
                    )
                }
              }
          }
      }

    def getBalance(chatId: Long): F[Balance] =
      withWallet(chatId) { wallet =>
        wallet.accounts
          .map(x => explorerService.getBalance(x.rawAddress))
          .sequence
          .map(_.reduce[Balance](_ merge _))
      }

    def exists(chatId: Long): F[Boolean] =
      walletRepo.readWallet(chatId).map(_.isDefined)

    private def addToUtxPool(id: ModifierId, chatId: Long): F[Unit] =
      utxPoolRef.update(_ add (id -> chatId))

    private def withWallet[A](chatId: Long)(f: Wallet => F[A]): F[A] =
      walletRepo
        .readWallet(chatId)
        .flatMap {
          _.fold[F[A]](MonadError[F, Throwable].raiseError(WalletNotFound))(f)
        }
  }

  object Live {

    /** Production wallet service smart constructor.
      */
    def apply[F[_]: Async](
      explorerService: ExplorerService[F],
      db: DB,
      utxPoolRef: Ref[F, UtxPool],
      settings: Settings
    ): Live[F] = {
      val storage = new LDBStorage[F](db)
      val walletRepo = new repositories.WalletRepo.Live[F](storage)
      new Live[F](utxPoolRef, explorerService, walletRepo, settings)
    }
  }
}
