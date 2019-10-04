package com.github.oskin1.wallet.models.network

import com.github.oskin1.wallet.BoxId
import io.circe.Decoder

final case class Box(id: BoxId, value: Long)

object Box {

  implicit val decoder: Decoder[Box] = { c =>
    for {
      id    <- c.downField("id").as[BoxId]
      value <- c.downField("value").as[Long]
    } yield Box(id, value)
  }
}
