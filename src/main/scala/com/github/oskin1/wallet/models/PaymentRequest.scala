package com.github.oskin1.wallet.models

import org.ergoplatform.ErgoAddress

final case class PaymentRequest(address: ErgoAddress, amount: Long) {
  override def toString: String = s"$address -> $amount"
}
