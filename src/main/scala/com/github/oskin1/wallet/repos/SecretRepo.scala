package com.github.oskin1.wallet.repos

import canoe.models.ChatId
import com.github.oskin1.wallet.EncryptedSecret
import com.github.oskin1.wallet.storage.Storage

trait SecretRepo[F[_]] {

  def putSecret(chatId: ChatId, secret: EncryptedSecret): F[Unit]

  def readSecret(chatId: ChatId): F[Option[EncryptedSecret]]
}

object SecretRepo {

  final class Live[F[_]](storage: Storage[F]) extends SecretRepo[F] {

    def putSecret(chatId: ChatId, secret: EncryptedSecret): F[Unit] = ???

    def readSecret(chatId: ChatId): F[Option[EncryptedSecret]] = ???
  }
}
