package com.github.oskin1.wallet

import com.github.oskin1.wallet.models.PaymentRequest
import org.ergoplatform.ErgoAddressEncoder
import org.scalatest.{Matchers, PropSpec}

class UserInputParserSpec extends PropSpec with Matchers {

  implicit private val encoder: ErgoAddressEncoder = ErgoAddressEncoder(
    ErgoAddressEncoder.TestnetNetworkPrefix
  )

  private val address = encoder
    .fromString("3WycCMYP9kXAfUQ3TYXU26Vg8UNvoJSY5cc8WaZrswh6sQuMJ8Vv")
    .get

  private val (ergAmtStr, nanoErgAmt) = (99, 99 * constants.CoinsInOneErg)

  private val inputsParserTestCases = Seq(
    (s"${address.toString}: $ergAmtStr", List(PaymentRequest(address, nanoErgAmt))),
    (
      s"${address.toString}: $ergAmtStr, ${address.toString}: $ergAmtStr, ",
      List(PaymentRequest(address, nanoErgAmt), PaymentRequest(address, nanoErgAmt))
    ),
    (
      s"${address.toString}:$ergAmtStr , ${address.toString}: $ergAmtStr, ${address.toString}: $ergAmtStr",
      List(
        PaymentRequest(address, nanoErgAmt),
        PaymentRequest(address, nanoErgAmt),
        PaymentRequest(address, nanoErgAmt)
      )
    )
  )

  private val ergParserValidTestCases = Seq(
    ".2"        -> .2d,
    ".01"       -> .01d,
    "12.2"      -> 12.2d,
    "123.98763" -> 123.98763d,
    "123"       -> 123d
  ).map(x => x._1 -> x._2 * constants.CoinsInOneErg)

  private val ergParserInvalidTestCases = Seq(
    "0.0000000001"                        -> "Max ERG decimal places overflow",
    "99999999999999999999999999999999"    -> "Max ERG amount overflow",
    "12.22222222222222222222222222222222" -> "Max ERG decimal places overflow",
    ".000"                                -> "0 is not a valid ERG amount"
  )

  property("parse inputs") {
    inputsParserTestCases.foreach {
      case (raw, expected) =>
        UserInputParser.parsePaymentRequests(raw) shouldBe Right(expected)
    }
  }

  property("parse valid ERG amount") {
    ergParserValidTestCases.foreach {
      case (raw, expected) =>
        UserInputParser.parseErgAmount(raw) shouldBe Right(expected)
    }
  }

  property("parse invalid ERG amount") {
    ergParserInvalidTestCases.foreach {
      case (raw, expectedMsg) =>
        UserInputParser.parseErgAmount(raw) shouldBe Left(expectedMsg)
    }
  }
}
