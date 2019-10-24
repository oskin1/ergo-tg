package com.github.oskin1.wallet.models

import com.github.oskin1.wallet.constants
import org.ergoplatform.ErgoAddress

final case class PaymentRequest(address: ErgoAddress, amount: Long) {
  override def toString: String =
    s"$address -> ${amount.toDouble / constants.CoinsInOneErg} ERG"
}
