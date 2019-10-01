package com.github.oskin1.wallet.models

import org.ergoplatform.ErgoAddress

final case class TransactionRequest(address: ErgoAddress, amount: Long)
