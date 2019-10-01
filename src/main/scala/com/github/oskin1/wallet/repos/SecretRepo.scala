package com.github.oskin1.wallet.repos

import canoe.models.ChatId
import cats.{Monad, MonadError}
import com.github.oskin1.wallet.storage.Storage
import org.ergoplatform.wallet.secrets.EncryptedSecret

trait SecretRepo[F[_]] {

  def putSecret(chatId: ChatId.Chat, secret: EncryptedSecret): F[Unit]

  def readSecret(chatId: ChatId.Chat): F[Option[EncryptedSecret]]
}

object SecretRepo {

  final class Live[F[_]: Monad](storage: Storage[F])(
    implicit F: MonadError[F, Throwable]
  ) extends SecretRepo[F] {

    def putSecret(
      chatId: ChatId.Chat,
      secret: EncryptedSecret
    ): F[Unit] =
      storage.putT[Long, EncryptedSecret](chatId.id, secret)

    def readSecret(chatId: ChatId.Chat): F[Option[EncryptedSecret]] =
      storage.getT[Long, EncryptedSecret](chatId.id)
  }
}
