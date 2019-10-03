package com.github.oskin1.wallet

import canoe.api._
import canoe.models.Chat
import canoe.syntax._
import com.github.oskin1.wallet.services.WalletService

object scenarios {

  def restoreWallet[F[_]: TelegramClient](
    implicit service: WalletService[F]
  ): Scenario[F, Unit] =
    for {
      chat     <- Scenario.start(command("restore").chat)
      mnemonic <- provideText(chat, "Enter your mnemonic phrase.")
      pass     <- provideText(chat, "Enter a password to protect your wallet.")
      mnemonicPass <- provideOptText(
                       chat,
                       "Enter a password to protect your mnemonic phrase."
                     )
      wallet <- Scenario.eval(
                 service.restoreWallet(chat.id, mnemonic, pass, mnemonicPass)
               )
      _ <- Scenario.eval(chat.send(wallet.verboseMsg))
    } yield ()

  def createWallet[F[_]: TelegramClient](
    implicit service: WalletService[F]
  ): Scenario[F, Unit] =
    for {
      chat <- Scenario.start(command("create").chat)
      pass <- provideText(
               chat,
               "Enter a strong password to protect your wallet."
             )
      mnemonicPass <- provideOptText(
                       chat,
                       "Enter a password to protect your mnemonic phrase."
                     )
      wallet <- Scenario.eval(
                 service.createWallet(chat.id, pass, mnemonicPass)
               )
      _ <- Scenario.eval(chat.send(wallet.verboseMsg))
    } yield ()

  def createTransaction[F[_]: TelegramClient](
    implicit service: WalletService[F]
  ): Scenario[F, Unit] = ???

  def getBalance[F[_]: TelegramClient](
    implicit service: WalletService[F]
  ): Scenario[F, Unit] =
    for {
      chat       <- Scenario.start(command("balance").chat)
      balanceOpt <- Scenario.eval(service.getBalance(chat.id))
      _ <- Scenario.eval(
            chat.send(balanceOpt.fold("Wallet not found.")(_.verboseMsg))
          )
    } yield ()

  private def provideText[F[_]: TelegramClient](
    chat: Chat,
    msg: String
  ): Scenario[F, String] =
    for {
      _   <- Scenario.eval(chat.send(msg))
      txt <- Scenario.next(text)
    } yield stripMargins(txt)

  private def provideOptText[F[_]: TelegramClient](
    chat: Chat,
    msg: String
  ): Scenario[F, Option[String]] =
    for {
      _   <- Scenario.eval(chat.send(msg + " (optional) type 'skip' to skip."))
      txt <- Scenario.next(text)
      txtOpt = if (txt.toLowerCase.startsWith("skip")) None
      else Some(stripMargins(txt))
    } yield txtOpt

  private def stripMargins(str: String): String = str
}
