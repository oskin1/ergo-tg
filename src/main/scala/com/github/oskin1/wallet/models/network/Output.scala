package com.github.oskin1.wallet.models.network

import com.github.oskin1.wallet.BoxId
import io.circe.Decoder

final case class Output(id: BoxId, value: Long)

object Output {

  implicit val decoder: Decoder[Output] = { c =>
    for {
      id    <- c.downField("id").as[BoxId]
      value <- c.downField("value").as[Long]
    } yield Output(id, value)
  }
}
