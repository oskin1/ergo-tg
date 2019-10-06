package com.github.oskin1.wallet

import canoe.api._
import canoe.syntax._
import canoe.models.PrivateChat
import cats.effect.concurrent.Ref
import cats.implicits._
import cats.{
  Applicative,
  Monad
}
import com.github.oskin1.wallet.models.network.Transaction
import com.github.oskin1.wallet.services.ExplorerService
import fs2._

/** Tracks unconfirmed transactions in the network
  * until they get into the blockchain.
  */
final class TransactionTracker[F[_]: TelegramClient: Monad](
  txPoolRef: Ref[F, UtxPool],
  explorerService: ExplorerService[F],
  settings: Settings
) {

  def run: Stream[F, Unit] =
    Stream(()).repeat
      .covary[F]
      .evalMap[F, Unit] { _ =>
        findConfirmedTxs
          .flatMap {
            _.map {
              case (tx, chatId) =>
                PrivateChat(chatId, None, None, None)
                  .send(
                    s"Your transaction was confirmed.\n" +
                    s"Id: ${tx.id}\nBlockId: ${tx.blockInfo.id}\n" +
                    s"NumConfirmations: ${tx.confirmationsNum}"
                  )
            }.sequence
          }
          .map(_ => ())
      }

  private def findConfirmedTxs: F[List[(Transaction, Long)]] =
    for {
      pool       <- txPoolRef.get
      lastHeight <- pool.heightOpt.fold(
                      explorerService.getBlockchainInfo
                        .map(_.height)
                    )(Applicative[F].pure(_))
      newTxs     <- explorerService.getTransactionsSince(lastHeight)
    } yield
      newTxs
        .filter(_.confirmationsNum >= settings.minConfirmationsNum)
        .flatMap { tx =>
          pool.txs
            .find(_._1 == tx.id)
            .map { case (_, chatId) => tx -> chatId }
        }
}
