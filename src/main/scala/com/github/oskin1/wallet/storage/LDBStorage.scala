package com.github.oskin1.wallet.storage
import cats.Applicative
import cats.effect.{Async, Resource, Sync}
import org.iq80.leveldb.DB

import scala.util.Try

final class LDBStorage[F[_]: Async](db: DB) extends Storage[F] {

  def get(key: K): F[Option[V]] =
    Async[F].async { cb =>
      cb(Try(db.get(key)).fold(Left(_), res => Right(Option(res))))
    }

  def put(items: Seq[(K, V)]): F[Unit] =
    (for {
      batch <- Resource.make(Sync[F].delay(db.createWriteBatch()))(
                 x => Sync[F].delay(x.close())
               )
      _     <- Resource.liftF(Sync[F].delay(items.foreach {
                 case (k, v) => batch.put(k, v)
               }))
      _     <- Resource.liftF(Async[F].async[Unit] { cb =>
                 cb(Try(db.write(batch)).fold(Left(_), res => Right(Option(res))))
               })
    } yield ()).use(_ => Applicative[F].unit)
}
