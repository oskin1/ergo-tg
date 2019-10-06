package com.github.oskin1.wallet.repositories

import cats.{Monad, MonadError}
import com.github.oskin1.wallet.models.storage.Wallet
import com.github.oskin1.wallet.persistence.Storage

trait WalletRepo[F[_]] {

  /** Persist a `wallet` associating it with a given `chatId`.
    */
  def putWallet(chatId: Long, wallet: Wallet): F[Unit]

  /** Get a wallet associated with a given `chatId` from persistent storage.
    */
  def readWallet(chatId: Long): F[Option[Wallet]]
}

object WalletRepo {

  final class Live[F[_]: Monad](storage: Storage[F])(
    implicit F: MonadError[F, Throwable]
  ) extends WalletRepo[F] {

    def putWallet(
      chatId: Long,
      wallet: Wallet
    ): F[Unit] =
      storage.putT(chatId, wallet)

    def readWallet(chatId: Long): F[Option[Wallet]] =
      storage.getT(chatId)
  }
}
