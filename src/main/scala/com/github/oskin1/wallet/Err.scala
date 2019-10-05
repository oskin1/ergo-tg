package com.github.oskin1.wallet

class Err(code: Int, msg: String) extends Error

case object WalletNotFound
  extends Err(code = 0, s"No wallet associated with this chat was found")
final case class AuthError(detail: Option[String] = None)
  extends Err(
    code = 1,
    s"Authentication failed: ${detail.getOrElse("no details")}"
  )
