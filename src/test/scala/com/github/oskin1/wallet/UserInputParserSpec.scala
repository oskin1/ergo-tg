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

  private val testCases = Seq(
    (s"${address.toString}: 10000", List(PaymentRequest(address, 10000L))),
    (
      s"${address.toString}: 1234522, ${address.toString}: 99999, ",
      List(PaymentRequest(address, 1234522L), PaymentRequest(address, 99999L))
    ),
    (
      s"${address.toString}:1234522 , ${address.toString}: 99999, ${address.toString}: 19839242",
      List(
        PaymentRequest(address, 1234522L),
        PaymentRequest(address, 99999L),
        PaymentRequest(address, 19839242L)
      )
    )
  )

  property("parse user input") {
    testCases.foreach { case (raw, expected) =>
      UserInputParser.parsePaymentRequests(raw) shouldBe Right(expected)
    }
  }
}
