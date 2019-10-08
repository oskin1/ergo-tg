package com.github.oskin1.wallet.models.network

import com.github.oskin1.wallet.ModifierId
import io.circe.Decoder

final case class Transaction(
  id: ModifierId,
  blockId: ModifierId,
  confirmationsNum: Int
)

object Transaction {

  implicit def decoder: Decoder[Transaction] = { c =>
    for {
      id            <- c.downField("id").as[ModifierId]
      block         <- c.downField("headerId").as[ModifierId]
      confirmations <- c.downField("confirmationsCount").as[Int]
    } yield Transaction(id, block, confirmations)
  }
}
