package com.github.oskin1.wallet

sealed class WalletError(val code: Int, val msg: String) extends Error

object WalletError {

  case object WalletNotFound
    extends WalletError(
      code = 0,
      s"No wallet associated with this chat was found"
    )

  final case class AuthError(detail: Option[String] = None)
    extends WalletError(
      code = 1,
      s"Authentication failed: ${detail.getOrElse("no details")}"
    )

  case object NotEnoughBoxes
    extends WalletError(
      code = 2,
      "Not enough coins to satisfy transaction"
    )

  case object WalletAlreadyExists
    extends WalletError(
      code = 3,
      "Wallet associated with this chat already exists"
    )
}
