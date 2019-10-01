package com.github.oskin1.wallet.models.network

import io.circe.Decoder

final case class Balance(confirmedBalance: Long, totalBalance: Long)

object Balance {

  implicit val decoder: Decoder[Balance] = { c =>
    for {
      confirmed <- c.downField("transactions")
                    .downField("confirmedBalance")
                    .as[Long]
      total     <- c.downField("transactions").downField("totalBalance").as[Long]
    } yield Balance(confirmed, total)
  }
}
