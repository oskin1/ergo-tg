package com.github.oskin1.wallet

import canoe.api.{Bot, TelegramClient}
import canoe.models.Update
import com.github.oskin1.wallet.services.WalletService
import com.github.oskin1.wallet.storage.DataBase
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.iq80.leveldb.DB
import fs2._
import pureconfig._
import pureconfig.generic.auto._
import zio._
import zio.interop.catz._

import scala.concurrent.ExecutionContext.global

object WalletApp extends CatsApp with DataBase {

  import scenarios._

  def program: Stream[Task, Update] =
    makeEnv.flatMap {
      case (settings, db, explorerClient, telegramClient) =>
        implicit val tc: TelegramClient[Task] = telegramClient
        implicit val ws: WalletService.Live[Task] =
          WalletService.Live(db, explorerClient, settings)
        Bot
          .polling[Task]
          .follow(
            restoreWallet,
            createWallet,
            createTransaction,
            getBalance
          )
    }

  def run(args: List[String]): ZIO[WalletApp.Environment, Nothing, Int] =
    program.compile.drain.fold(_ => 1, _ => 0)

  private def makeEnv =
    for {
      settings <- Stream.eval(
                    Task.effect(ConfigSource.default.loadOrThrow[Settings])
                  )
      token <- Stream.eval(Task.effect(System.getenv("BOT_TOKEN")))
      telegramClient <- Stream.resource[Task, TelegramClient[Task]](
                          TelegramClient.global[Task](token)
                        )
      explorerClient <- Stream.resource[Task, Client[Task]](
                          BlazeClientBuilder[Task](global).resource
                        )
      db <- Stream.resource[Task, DB](makeDb[Task](settings.storagePath))
    } yield (settings, db, explorerClient, telegramClient)

}
