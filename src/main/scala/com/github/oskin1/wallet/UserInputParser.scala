package com.github.oskin1.wallet

import cats.implicits._
import com.github.oskin1.wallet.models.PaymentRequest
import fastparse.SingleLineWhitespace._
import fastparse._
import org.ergoplatform.ErgoAddressEncoder

object UserInputParser {

  private def base58Chars[_: P]: P[Unit] =
    CharIn("123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")

  private def base58String[_: P]: P[String] =
    P(base58Chars.rep(2).!)

  private def posLong[_: P]: P[Long] = P(CharIn("0-9").rep(1).!.map(_.toLong))

  private def paymentRequest[_: P]: P[(String, Long)] =
    P(base58String ~ ":" ~ posLong)

  private def paymentRequests[_: P]: P[Seq[(String, Long)]] =
    paymentRequest.rep(1, sep = ",")

  /** Parse user input of expected format into a list of payment requests.
    */
  def parsePaymentRequests(
    input: String
  )(implicit e: ErgoAddressEncoder): Either[String, List[PaymentRequest]] =
    parse(input, paymentRequests(_)).fold(
      (msg, _, _) => Left(msg),
      (requests, _) =>
        requests
          .map { case (addr, amt) => e.fromString(addr).map(_ -> amt) }
          .toList
          .sequence
          .fold[Either[String, List[PaymentRequest]]](
            _ => Left("ErgoAddress decoding failure"),
            r => Right(r.map(x => PaymentRequest(x._1, x._2)))
        )
    )

  /** Parse user input into positive long integer.
   */
  def parsePosLong(input: String): Either[String, Long] =
    parse(input, posLong(_))
      .fold((msg, _, _) => Left(msg), (num, _) => Right(num))
}
