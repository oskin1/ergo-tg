package com.github.oskin1.wallet.models.network

import io.circe.Decoder

final case class Balance(confirmedBalance: Long, totalBalance: Long) {

  def merge(that: Balance): Balance =
    Balance(
      this.confirmedBalance + that.confirmedBalance,
      this.totalBalance + that.totalBalance
    )

  def verboseMsg: String =
    s"Confirmed balance is:\n`$confirmedBalance` nanoErg\n" +
    s"Total balance is (incl. unconfirmed txs):\n`$totalBalance` nanoErg."
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
