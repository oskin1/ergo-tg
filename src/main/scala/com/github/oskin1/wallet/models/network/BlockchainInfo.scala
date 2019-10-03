package com.github.oskin1.wallet.models.network

import io.circe.Decoder

final case class BlockchainInfo(height: Int) extends AnyVal

object BlockchainInfo {

  implicit val decoder: Decoder[BlockchainInfo] = { c =>
    for {
      height <- c.downField("total").as[Int]
    } yield BlockchainInfo(height)
  }
}
