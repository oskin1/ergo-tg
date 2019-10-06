package com.github.oskin1.wallet.persistence

import java.io.File

import cats.effect.{Resource, Sync}
import org.iq80.leveldb.{DB, Options}

trait DataBase extends LDBFactory {

  def makeDb[F[_]](path: String)(implicit F: Sync[F]): Resource[F, DB] =
    for {
      path    <- Resource.liftF(Sync[F].delay(new File(path)))
      _       <- Resource.liftF(Sync[F].delay(path.mkdirs()))
      options = new Options()
      _       <- Resource.liftF(Sync[F].delay(options.createIfMissing()))
      factory <- Resource.liftF(loadFactory)
      db      <- Resource.make(Sync[F].delay(factory.open(path, options)))(
                   db => Sync[F].delay(db.close())
                 )
    } yield db
}
