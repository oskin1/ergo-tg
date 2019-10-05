package com.github.oskin1.wallet

import canoe.api._
import canoe.models.Update
import canoe.syntax._
import com.github.oskin1.wallet.services.WalletService
import com.github.oskin1.wallet.storage.DataBase
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

  def run(args: List[String]): ZIO[WalletApp.Environment, Nothing, Int] =
    program.compile.drain.fold(e => {println(e); 1}, _ => 0)

  private def program: Stream[Task, Update] =
    makeEnv.flatMap {
      case (settings, db, explorerClient, telegramClient) =>
        implicit val tc: TelegramClient[Task] = telegramClient
        implicit val ws: WalletService.Live[Task] =
          WalletService.Live(db, explorerClient, settings)
        implicit val encoder: ErgoAddressEncoder = settings.addressEncoder
        Bot
          .polling[Task]
          .follow(cancellableScenarios:_*)
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
    } yield (settings, db, client, tgClient)

}
