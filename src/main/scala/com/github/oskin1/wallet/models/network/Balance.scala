package com.github.oskin1.wallet.models.network

import io.circe.Decoder

final case class Balance(confirmedBalance: Long, totalBalance: Long) {

  def merge(that: Balance): Balance =
    Balance(
      this.confirmedBalance + that.confirmedBalance,
      this.totalBalance + that.totalBalance
    )
}

object Balance {

  implicit val decoder: Decoder[Balance] = { c =>
    for {
      confirmed <- c.downField("transactions")
                    .downField("confirmedBalance")
                    .as[Long]
      total <- c.downField("transactions").downField("totalBalance").as[Long]
    } yield Balance(confirmed, total)
  }

  def empty: Balance = Balance(0L, 0L)
}
