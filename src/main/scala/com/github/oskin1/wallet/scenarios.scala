package com.github.oskin1.wallet

import canoe.api.{Scenario, _}
import canoe.models.Chat
import canoe.syntax._
import cats.implicits._
import cats.{ApplicativeError, Functor}
import com.github.oskin1.wallet.models.PaymentRequest
import com.github.oskin1.wallet.services.WalletService
import org.ergoplatform.ErgoAddressEncoder

object scenarios {

  private val willBeDeleted: String =
    "(after you send the message it will be deleted)"

  private val minFeeAmount: Long = 1000000L

  def restoreWallet[F[_]: TelegramClient: Functor](
    implicit service: WalletService[F]
  ): Scenario[F, Unit] =
    for {
      chat <- Scenario.start(command("restoreWallet").chat)
      _ <- Scenario.eval(
             chat.send(s"Enter your mnemonic phrase. $willBeDeleted")
           )
      mnemonic     <- enterTextSecure(chat)
      pass         <- providePass(chat)
      mnemonicPass <- enterMnemonicPass(chat)
      wallet <- Scenario.eval(
                  service.restoreWallet(chat.id, mnemonic, pass, mnemonicPass)
                )
      _ <- Scenario.eval(chat.send(wallet.verboseMsg))
    } yield ()

  def createWallet[F[_]: TelegramClient](
    implicit service: WalletService[F]
  ): Scenario[F, Unit] =
    for {
      chat         <- Scenario.start(command("createWallet").chat)
      pass         <- providePass(chat)
      mnemonicPass <- provideMnemonicPass(chat)
      wallet       <- Scenario.eval(
                        service.createWallet(chat.id, pass, mnemonicPass)
                      )
      _            <- Scenario.eval(chat.send(wallet.verboseMsg))
    } yield ()

  def createTransaction[F[_]: TelegramClient](
    implicit
    F: ApplicativeError[F, Throwable],
    service: WalletService[F],
    encoder: ErgoAddressEncoder
  ): Scenario[F, Unit] =
    for {
      chat   <- Scenario.start(command("payment").chat)
      exists <- Scenario.eval(service.exists(chat.id))
      _ <- if (exists)
             for {
               requests <- providePayments(chat)
               fee      <- provideFee(chat)
               _        <- completeTx(chat, requests, fee)
             } yield ()
           else Scenario.eval(chat.send("You need to create wallet first."))
    } yield ()

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

  private def providePayments[F[_]: TelegramClient](chat: Chat)(
    implicit encoder: ErgoAddressEncoder
  ): Scenario[F, List[PaymentRequest]] =
    for {
      _ <- Scenario.eval(
            chat.send(
              "Specify as many payments as you want in the following format: " +
              "[address_1]:[amount_nano_ergs], [address_2]:[amount_nano_ergs], ..." +
              "Example:\n9hR8SC8GcPfse8vScLZ6fNkn8JtaQZgnKziqoQ3H5SPCPtY5JgC:1000000000"
            )
          )
      in <- Scenario.next(text)
      requests <- UserInputParser
                   .parsePaymentRequests(in)
                   .fold(
                     e =>
                       Scenario
                         .eval(chat.send(s"Wrong input format: $e")) >> providePayments(
                         chat
                     ),
                     r => Scenario.pure[F, List[PaymentRequest]](r)
                   )
    } yield requests

  private def provideFee[F[_]: TelegramClient](chat: Chat): Scenario[F, Long] =
    for {
      _ <- Scenario.eval(
             chat.send(
               "Specify fee amount. Minimum is 1000000 nanoErg."
             )
           )
      in <- Scenario.next(text)
      fee <- UserInputParser
              .parsePosLong(in)
              .fold(
                e =>
                  Scenario.eval(
                    chat.send(s"Wrong input format: $e")
                  ) >> provideFee(chat),
                r =>
                  if (r >= minFeeAmount) Scenario.pure[F, Long](r)
                  else
                    Scenario.eval(
                      chat.send(s"Fee amount is too small. Try again.")
                    ) >> provideFee(chat)
              )
    } yield fee

  private def completeTx[F[_]: TelegramClient](
    chat: Chat,
    requests: List[PaymentRequest],
    fee: Long
  )(
    implicit
    F: ApplicativeError[F, Throwable],
    service: WalletService[F]
  ): Scenario[F, Unit] =
    for {
      _ <- Scenario.eval(
            chat.send(
              "Enter your password to confirm the following payments: " + requests
                .map(_.toString)
                .mkString(",\n")
            )
          )
      pass <- enterTextSecure(chat)
      eitherId <- Scenario.eval(
                   service
                     .createTransaction(chat.id, pass, requests, fee)
                     .map[Either[AuthError, String]](Right(_))
                     .handleErrorWith {
                       case e: AuthError => F.pure(Left(e))
                       case e            => F.raiseError(e)
                     }
                 )
      _ <- eitherId.fold(
            e =>
              Scenario.eval(chat.send(s"$e. Try again.")) >> completeTx(
                chat,
                requests,
                fee
            ),
            id =>
              Scenario.eval(
                chat.send(
                  s"Your transaction was submitted to the network. ID: $id"
                )
            )
          )
    } yield ()

  private def enterMnemonicPass[F[_]: TelegramClient: Functor](
    chat: Chat
  ): Scenario[F, Option[String]] =
    for {
      _ <- Scenario.eval(
            chat.send(
              "Enter a password for your mnemonic phrase." +
              "Or type 'skip' if you don't have one."
            )
          )
      msg <- Scenario.next(textMessage)
      txtOpt = if (msg.text.toLowerCase.startsWith("skip")) None
               else Some(stripMargins(msg.text))
      _ <- txtOpt.fold[Scenario[F, Unit]](Scenario.done[F])(
             _ => Scenario.eval(msg.delete.map(_ => ()))
           )
    } yield txtOpt

  private def providePass[F[_]: TelegramClient](
    chat: Chat
  ): Scenario[F, String] =
    for {
      _ <- Scenario.eval(
            chat.send(
              s"Enter a password to protect your wallet. $willBeDeleted"
            )
          )
      pass      <- enterTextSecure(chat)
      _         <- Scenario.eval(chat.send("Repeat your password"))
      reentered <- enterTextSecure(chat)
      _ <- if (pass == reentered) Scenario.done[F]
           else
             Scenario.eval(
               chat.send("Provided passwords don't match. Try again.")
             ) >> providePass(chat)
    } yield pass

  private def provideMnemonicPass[F[_]: TelegramClient](
    chat: Chat
  ): Scenario[F, Option[String]] =
    Scenario
      .eval(
        chat.send(
          "Enter a password to protect your mnemonic phrase. " +
          "(optional) type 'skip' to skip this step."
        )
      )
      .flatMap { _ =>
        Scenario.next(textMessage).flatMap {
          case msg if msg.text.toLowerCase.startsWith("skip") =>
            Scenario.pure[F, Option[String]](None)
          case msg =>
            for {
              _         <- Scenario.eval(msg.delete)
              _         <- Scenario.eval(chat.send("Repeat your password"))
              reentered <- enterTextSecure(chat)
              _ <- if (msg.text == reentered) Scenario.done[F]
                   else
                     Scenario.eval(
                       chat.send("Provided passwords don't match. Try again.")
                     ) >> provideMnemonicPass(chat)
            } yield Some(stripMargins(msg.text))
        }
      }

  private def enterTextSecure[F[_]: TelegramClient](
    chat: Chat
  ): Scenario[F, String] =
    for {
      message <- Scenario.next(textMessage)
      _       <- Scenario.eval(message.delete)
    } yield stripMargins(message.text)

  private def stripMargins(str: String): String = str
}
