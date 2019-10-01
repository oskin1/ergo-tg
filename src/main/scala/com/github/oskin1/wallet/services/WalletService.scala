package com.github.oskin1.wallet.services

import canoe.models.ChatId
import com.github.oskin1.wallet.models.{
  NewWallet,
  RestoredWallet,
  TransactionRequest
}
import com.github.oskin1.wallet.storage.Storage

/** Provides actual wallet functionality.
  */
trait WalletService[F[_]] {

  def restoreWallet(mnemonic: String, pass: String): F[RestoredWallet]

  def createWallet(pass: String): F[NewWallet]

  def createTransaction(
    chatId: ChatId,
    pass: String,
    requests: Seq[TransactionRequest],
    fee: Long
  ): F[String]

  def checkBalance(chatId: ChatId): F[Long]
}

object WalletService {

  final class Live[F[_]](
    explorerService: ExplorerService[F],
    storage: Storage[F]
  ) extends WalletService[F] {

    def restoreWallet(mnemonic: String, pass: String): F[RestoredWallet] = ???

    def createWallet(pass: String): F[NewWallet] = ???

    def createTransaction(
      chatId: ChatId,
      pass: String,
      requests: Seq[TransactionRequest],
      fee: Long
    ): F[String] = ???

    def checkBalance(chatId: ChatId): F[Long] = ???
  }
}
