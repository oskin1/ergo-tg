package com.github.oskin1.wallet

import canoe.api.{Bot, TelegramClient}
import canoe.models.Update
import fs2._
import zio._
import zio.interop.catz._

object WalletApp extends CatsApp {

  import scenarios._

  def program: Stream[Task, Update] =
    Stream.eval(Task.effect(System.getenv("BOT_TOKEN"))).flatMap { token =>
      Stream
        .resource[Task, TelegramClient[Task]](
          TelegramClient.global[Task](token)
        )
        .flatMap { implicit client =>
          Bot.polling[Task].follow(???)
        }
    }

  def run(args: List[String]): ZIO[WalletApp.Environment, Nothing, Int] =
    ???

}
