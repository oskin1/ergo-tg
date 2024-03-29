package com.github.oskin1.wallet

import canoe.api._
import canoe.models.messages.TextMessage
import canoe.models.outgoing.TextContent
import canoe.models.{ParseMode, PrivateChat}
import cats.effect.concurrent.Ref
import cats.effect.{Sync, Timer}
import cats.implicits._
import com.github.oskin1.wallet.models.network.Transaction
import com.github.oskin1.wallet.modules.Logging
import com.github.oskin1.wallet.persistence.UtxPool
import com.github.oskin1.wallet.services.ExplorerService
import com.github.oskin1.wallet.settings.Settings
import fs2._

/** Tracks unconfirmed transactions in the network
  * until they get into the blockchain.
  */
final class TransactionTracker[F[_]: TelegramClient: Timer: Sync](
  txPoolRef: Ref[F, UtxPool],
  explorerService: ExplorerService[F],
  settings: Settings
) extends Logging {

  /** Poll network explorer in order to find confirmed transactions
    * from UTX pool and notify users who sent them.
    */
  def run: Stream[F, Unit] =
    Stream(()).repeat
      .covary[F]
      .metered(settings.explorerPollingInterval)
      .evalMap[F, Unit] { _ =>
        findConfirmedTxs
          .flatMap {
            _.map { case (tx, chatId) => notify(chatId, tx) }.sequence
          }
          .map(_ => ())
          .handleErrorWith(e => Sync[F].delay(log.error(e)(e.getMessage)))
      }

  /** Notify wallet holder associated with a given `chatId`
    * of `tx` confirmation.
    */
  private def notify(chatId: Long, tx: Transaction): F[TextMessage] =
    PrivateChat(chatId, None, None, None)
      .send[F, TextMessage](
        TextContent(
          s"Your transaction was confirmed.\n" +
          s"Id: [${tx.id}](${settings.explorerUrl}/transactions/${tx.id})\n" +
          s"BlockId: [${tx.blockId}](${settings.explorerUrl}/blocks/${tx.blockId})\n" +
          s"NumConfirmations: `${tx.confirmationsNum}`",
          Some(ParseMode.Markdown)
        )
      )

  /** Fetch new transactions appeared in the network recently
    * and match them with transactions from UTX pool.
    */
  private def findConfirmedTxs: F[List[(Transaction, Long)]] =
    for {
      pool        <- txPoolRef.get
      networkInfo <- explorerService.getBlockchainInfo
      lastHeight   = pool.heightOpt.getOrElse(networkInfo.height)
      newTxs      <- explorerService.getTransactionsSince(
                       lastHeight - settings.minConfirmationsNum
                     )
      confirmed    = findConfirmedTxs(newTxs, pool)
      confirmedIds = confirmed.map(_._1.id)
      _           <- Sync[F].delay(log.info(s"${confirmed.size} txs confirmed."))
      _           <- txPoolRef.update(
                       _
                         .filterNot(confirmedIds.contains)
                         .setHeight(networkInfo.height)
                     )
    } yield confirmed

  private def findConfirmedTxs(
    txs: List[Transaction],
    txsPool: UtxPool
  ): List[(Transaction, Long)] =
    txs
      .filter(_.confirmationsNum >= settings.minConfirmationsNum)
      .flatMap { tx =>
        txsPool.txs
          .find(_._1 == tx.id)
          .map { case (_, chatId) => tx -> chatId }
      }
}
