package com.github.oskin1.wallet.repositories

import cats.{Monad, MonadError}
import com.github.oskin1.wallet.models.storage.Wallet
import com.github.oskin1.wallet.storage.Storage

trait WalletRepo[F[_]] {

  def putWallet(chatId: Long, wallet: Wallet): F[Unit]

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
      storage.putT[Long, Wallet](chatId, wallet)

    def readWallet(chatId: Long): F[Option[Wallet]] =
      storage.getT[Long, Wallet](chatId)
  }
}
