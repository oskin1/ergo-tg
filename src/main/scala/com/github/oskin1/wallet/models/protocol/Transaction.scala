package com.github.oskin1.wallet.models.protocol

import com.github.oskin1.wallet.ModifierId
import io.circe.Decoder

final case class Transaction(
  id: ModifierId,
  block: Block,
  confirmationsNum: Int
)

object Transaction {

  implicit def decoder: Decoder[Transaction] = { c =>
    for {
      id            <- c.downField("id").as[ModifierId]
      block         <- c.downField("block").as[Block]
      confirmations <- c.downField("confirmationsCount").as[Int]
    } yield Transaction(id, block, confirmations)
  }

}
