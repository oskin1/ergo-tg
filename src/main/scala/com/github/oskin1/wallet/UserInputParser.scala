package com.github.oskin1.wallet

import cats.implicits._
import com.github.oskin1.wallet.models.PaymentRequest
import com.github.oskin1.wallet.parsers._
import fastparse._
import org.ergoplatform.ErgoAddressEncoder

import scala.util.Try

object UserInputParser {

  /** Parse user input of expected format into a list of payment requests.
    * @return - Right(requests) in case `input` is a valid string repr of payment requests,
    *           Left(errorMessage) otherwise.
    */
  def parsePaymentRequests(
    input: String
  )(implicit e: ErgoAddressEncoder): Either[String, List[PaymentRequest]] =
    parseWith(input, paymentRequests(_)) { requests =>
      requests
        .map { case (addr, amt) => e.fromString(addr).map(_ -> amt) }
        .toList
        .sequence
        .fold[Either[String, List[PaymentRequest]]](
          _ => Left("ErgoAddress decoding failure"),
          _.map {
            case (addr, amt) =>
              parseErgAmount(amt).map(PaymentRequest(addr, _))
          }.sequence
        )
    }

  /** Parse a valid ERG amount from its string representation.
    * @return - Right(amount) in case `s` is a valid string repr of ergo amount,
    *           Left(errorMessage) otherwise.
    */
  def parseErgAmount(input: String): Either[String, Long] =
    parseWith(input, posNum(_)) { s =>
      Try(s.toDouble).fold(
        e => Left(e.getMessage),
        validateNum(_).map(x => (x * constants.CoinsInOneErg).toLong)
      )
    }

  private def parseWith[A, B](in: String, p: P[_] => P[A])(
    fn: A => Either[String, B]
  ): Either[String, B] =
    parse(in, p).fold((msg, _, _) => Left(msg), (r, _) => fn(r))

  private def validateNum(num: Double): Either[String, Double] =
    if (BigDecimal(num).scale > constants.ErgoDecimalPlaces)
      Left("Max ERG decimal places overflow")
    else if (num > Long.MaxValue) Left("Max ERG amount overflow")
    else if (num == 0) Left("0 is not a valid ERG amount")
    else Right(num)
}
