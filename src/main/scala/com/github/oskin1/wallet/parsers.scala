package com.github.oskin1.wallet

import fastparse.SingleLineWhitespace._
import fastparse._

object parsers {

  def base58Chars[_: P]: P[Unit] =
    CharIn("123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")

  def base58String[_: P]: P[String] =
    P(base58Chars.rep(2).!)

  def posNum[_: P]: P[String] =
    ((CharsWhileIn("0-9").? ~~ ".").? ~~ CharsWhileIn("0-9")).!

  def paymentRequest[_: P]: P[(String, String)] =
    base58String ~ ":" ~ posNum

  def paymentRequests[_: P]: P[Seq[(String, String)]] =
    paymentRequest.rep(1, sep = ",")
}
