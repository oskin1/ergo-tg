package com.github.oskin1.wallet

import canoe.api._
import canoe.models.{Chat, ParseMode}
import canoe.models.messages.TelegramMessage
import canoe.models.outgoing.TextContent
import canoe.syntax._
import cats.implicits._
import cats.{ApplicativeError, Functor}
import com.github.oskin1.wallet.WalletError.AuthError
import com.github.oskin1.wallet.models.PaymentRequest
import com.github.oskin1.wallet.services.WalletService
import org.ergoplatform.ErgoAddressEncoder

object scenarios {

  private val willBeDeleted: String =
    "_(the message will be deleted as you send it)_"

  private val minFeeAmount: Long = 1000000L

  private implicit def textContent(text: String): TextContent =
    TextContent(text, Some(ParseMode.Markdown))

  /** Restore from existing mnemonic phrase, associate
    * it with a given chatId and persist it.
    */
  def restoreWallet[F[_]: TelegramClient: Functor](
    implicit
    F: ApplicativeError[F, Throwable],
    service: WalletService[F]
  ): Scenario[F, Unit] =
    for {
      chat   <- Scenario.start(command("restore_wallet").chat)
      exists <- Scenario.eval(service.exists(chat.id))
      _      <- if (exists) Scenario.eval(chat.send("Wallet already exists."))
                else completeRestore(chat)
    } yield ()

  private def completeRestore[F[_]: TelegramClient: Functor](chat: Chat)(
    implicit
    F: ApplicativeError[F, Throwable],
    service: WalletService[F]
  ): Scenario[F, Unit] =
    for {
      _            <- Scenario.eval(
                        chat.send(s"Enter your mnemonic phrase.\n$willBeDeleted")
                      )
      mnemonic     <- enterTextSecure(chat)
      pass         <- providePass(chat)
      mnemonicPass <- enterMnemonicPass(chat)
      walletE      <- eval(service.restoreWallet(chat.id, mnemonic, pass, mnemonicPass))
      _            <- walletE.fold(
                        e      => msgError(e)(chat),
                        wallet => Scenario.eval(chat.send(wallet.verboseMsg))
                      )
    } yield ()

  /** Create new wallet, associate it with a given
    * chatId and persist it.
    */
  def createWallet[F[_]: TelegramClient](
  implicit
  F: ApplicativeError[F, Throwable],
  service: WalletService[F]
  ): Scenario[F, Unit] =
    for {
      chat   <- Scenario.start(command("create_wallet").chat)
      exists <- Scenario.eval(service.exists(chat.id))
      _      <- if (exists) Scenario.eval(chat.send("Wallet already exists."))
                else completeCreate(chat)
    } yield ()

  private def completeCreate[F[_]: TelegramClient](chat: Chat)(
    implicit
    F: ApplicativeError[F, Throwable],
    service: WalletService[F]
  ): Scenario[F, Unit] =
    for {
      pass         <- providePass(chat)
      mnemonicPass <- provideMnemonicPass(chat)
      walletE      <- eval(service.createWallet(chat.id, pass, mnemonicPass))
      _            <- walletE.fold(
                        e      => msgError(e)(chat),
                        wallet => Scenario.eval(chat.send(wallet.verboseMsg))
                      )
    } yield ()

  /** Delete wallet from service.
    */
  def deleteWallet[F[_]: TelegramClient](
    implicit
    F: ApplicativeError[F, Throwable],
    service: WalletService[F]
  ): Scenario[F, Unit] =
    for {
      chat    <- Scenario.start(command("delete_wallet").chat)
      _       <- Scenario.eval(chat.send("Enter your password to *confirm* wallet deletion"))
      pass    <- enterTextSecure(chat)
      resultE <- eval(service.deleteWallet(chat.id, pass))
      _       <- resultE.fold(
                   e => msgError(e)(chat),
                   _ => Scenario.eval(chat.send("Your wallet was deleted"))
                 )
    } yield ()

  /** Create new transaction and submit it to the network.
    */
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

  /** Get an aggregated balance for a given chatId from the network.
    */
  def getBalance[F[_]: TelegramClient](
    implicit
    F: ApplicativeError[F, Throwable],
    service: WalletService[F]
  ): Scenario[F, Unit] =
    for {
      chat       <- Scenario.start(command("balance").chat)
      balanceE   <- eval(service.getBalance(chat.id))
      _          <- balanceE.fold(
                      e       => msgError(e)(chat),
                      balance => Scenario.eval(chat.send(balance.verboseMsg))
                    )
    } yield ()

  /** Payment requests input handler.
    * A retry is initiated in case a format of a user input is violated.
    */
  private def providePayments[F[_]: TelegramClient](chat: Chat)(
    implicit encoder: ErgoAddressEncoder
  ): Scenario[F, List[PaymentRequest]] =
    for {
      _ <- Scenario.eval(
            chat.send(
              "Specify as many payments as you want in the following format:\n" +
              "`[address_1]:[amount_nano_ergs], [address_2]:[amount_nano_ergs], ...`\n" +
              "*Example:*\n`9hR8SC8GcPfse8vScLZ6fNkn8JtaQZgnKziqoQ3H5SPCPtY5JgC:1000000000`"
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

  /** Fee input handler.
    * A retry is initiated in case a format of a user input is violated.
    */
  private def provideFee[F[_]: TelegramClient](chat: Chat): Scenario[F, Long] =
    for {
      _ <- Scenario.eval(
             chat.send(
               s"Specify fee amount. Minimum is `$minFeeAmount` nanoErg."
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

  /** Handles transaction confirmation and submission.
    * A retry is initiated in case wrong password is provided by user.
    */
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
              "Enter your password to *confirm* the following payments:\n`" + requests
                .map(_.toString)
                .mkString(",\n") + "`"
            )
          )
      pass <- enterTextSecure(chat)
      idE  <- eval(service.createTransaction(chat.id, pass, requests, fee))
      _ <- idE match {
             case Left(e: AuthError) =>
               Scenario.eval(chat.send(s"$e. Try again.")) >> completeTx(chat, requests, fee)
             case Left(e) =>
               msgError(e)(chat)
             case Right(id) =>
               Scenario.eval(
                 chat.send(
                   s"Your transaction was submitted to the network.\nId: `$id`"
                 )
               )
           }
    } yield ()

  /** Handles an input of optional mnemonic pass.
    */
  private def enterMnemonicPass[F[_]: TelegramClient: Functor](
    chat: Chat
  ): Scenario[F, Option[String]] =
    for {
      _ <- Scenario.eval(
            chat.send(
              "Enter a password for your mnemonic phrase.\n" +
              "Or type 'skip' if you don't have one."
            )
          )
      msg <- Scenario.next(textMessage)
      txtOpt = if (msg.text.toLowerCase.startsWith("skip")) None
               else Some(msg.text)
      _ <- txtOpt.fold[Scenario[F, Unit]](Scenario.done[F])(
             _ => Scenario.eval(msg.delete.map(_ => ()))
           )
    } yield txtOpt

  /** Handles password creation.
    * A retry is initiated in case user-provided passwords don't match.
    */
  private def providePass[F[_]: TelegramClient](
    chat: Chat
  ): Scenario[F, String] =
    for {
      _ <- Scenario.eval(
             chat.send(
               s"Enter a password to protect your wallet.\n$willBeDeleted"
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

  /** Handles optional mnemonic password creation.
    * A retry is initiated in case user-provided passwords don't match.
    */
  private def provideMnemonicPass[F[_]: TelegramClient](
    chat: Chat
  ): Scenario[F, Option[String]] =
    Scenario
      .eval(
        chat.send(
          "Enter a password to protect your mnemonic phrase.\n" +
          "_(optional) type 'skip' to skip this step._"
        )
      )
      .flatMap { _ =>
        Scenario.next(text).flatMap {
          case msg if msg.toLowerCase.startsWith("skip") =>
            Scenario.pure[F, Option[String]](None)
          case msg =>
            for {
              _         <- Scenario.eval(chat.send("Repeat your password"))
              reentered <- Scenario.next(text)
              _ <- if (msg == reentered) Scenario.done[F]
                   else
                     Scenario.eval(
                       chat.send("Provided passwords don't match. Try again.")
                     ) >> provideMnemonicPass(chat)
            } yield Some(msg)
        }
      }

  /** Handles an input of security critical data (such as passwords).
    * The message with an input is deleted as soon as it is processed.
    */
  private def enterTextSecure[F[_]: TelegramClient](
    chat: Chat
  ): Scenario[F, String] =
    for {
      message <- Scenario.next(textMessage)
      _       <- Scenario.eval(message.delete)
    } yield message.text

  private def eval[F[_], A](fa: F[A])(
    implicit F: ApplicativeError[F, Throwable]
  ): Scenario[F, Either[Throwable, A]] =
    Scenario.eval(fa.map[Either[Throwable, A]](Right(_)).handleError(Left(_)))

  private def msgError[F[_]: TelegramClient](
    e: Throwable
  )(chat: Chat): Scenario[F, TelegramMessage] =
    e match {
      case e: WalletError => Scenario.eval(chat.send(e.msg))
      case e              => Scenario.eval(chat.send(s"Error: ${e.getMessage}"))
    }
}
