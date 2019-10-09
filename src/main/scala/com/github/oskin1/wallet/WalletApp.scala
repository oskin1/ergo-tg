package com.github.oskin1.wallet

import canoe.api._
import canoe.models.Update
import canoe.syntax._
import cats.effect.Timer
import cats.effect.concurrent.Ref
import com.github.oskin1.wallet.services.WalletService
import com.github.oskin1.wallet.settings.Settings
import com.github.oskin1.wallet.persistence.{DataBase, UtxPool}
import fs2._
import org.ergoplatform.ErgoAddressEncoder
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.iq80.leveldb.DB
import pureconfig._
import pureconfig.generic.auto._
import zio._
import zio.interop.catz._

import scala.concurrent.ExecutionContext.global

object WalletApp extends CatsApp with DataBase {

  import scenarios._

  implicit val taskTimer: Timer[Task] = implicits.ioTimer[Throwable]

  def run(args: List[String]): ZIO[WalletApp.Environment, Nothing, Int] =
    program.compile.drain.fold(e => {println(e); 1}, _ => 0)

  private def program: Stream[Task, Update] =
    makeEnv.flatMap {
      case (settings, db, client, telegramClient, txPool) =>
        implicit val tc: TelegramClient[Task] = telegramClient
        implicit val encoder: ErgoAddressEncoder = settings.addressEncoder
        val explorerService = new services.ExplorerService.Live[Task](client, settings)
        implicit val ws: WalletService.Live[Task] =
          WalletService.Live(explorerService, db, txPool, settings)
        val txTracker = new TransactionTracker[Task](txPool, explorerService, settings)
        val bot = Bot.polling[Task]
        bot.follow(cancellableScenarios:_*) concurrently txTracker.run
    }

  private def cancellableScenarios(
    implicit
    tc: TelegramClient[Task],
    ws: WalletService[Task],
    ae: ErgoAddressEncoder
  ): Seq[Scenario[Task, Unit]] =
    Seq(
      restoreWallet,
      createWallet,
      deleteWallet,
      createTransaction,
      getBalance
    ).map(_.cancelWhen(command("cancel")))

  private def makeEnv =
    for {
      settings <- Stream.eval(
                    Task.effect(ConfigSource.default.loadOrThrow[Settings])
                  )
      token    <- Stream.eval(Task.effect(System.getenv("BOT_TOKEN")))
      tgClient <- Stream.resource[Task, TelegramClient[Task]](
                    TelegramClient.global[Task](token)
                  )
      client   <- Stream.resource[Task, Client[Task]](
                    BlazeClientBuilder[Task](global).resource
                  )
      db       <- Stream.resource[Task, DB](makeDb[Task](settings.storagePath))
      txPool   <- Stream.eval(Ref.of[Task, UtxPool](UtxPool.empty))
    } yield (settings, db, client, tgClient, txPool)
}
