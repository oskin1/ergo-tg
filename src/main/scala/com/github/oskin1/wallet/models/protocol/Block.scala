package com.github.oskin1.wallet.models.protocol

import com.github.oskin1.wallet.ModifierId
import io.circe.Decoder

final case class Block(id: ModifierId, height: Int)

object Block {

  implicit def decoder: Decoder[Block] = { c =>
    for {
      id     <- c.downField("id").as[ModifierId]
      height <- c.downField("height").as[Int]
    } yield Block(id, height)
  }
}
