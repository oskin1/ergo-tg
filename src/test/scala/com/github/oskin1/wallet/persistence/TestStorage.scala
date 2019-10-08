package com.github.oskin1.wallet.persistence

import cats.effect.IO
import scorex.util.encode.Base16

import scala.collection.mutable

trait TestStorage {

  def makeTestStorage: Storage[IO] = new Storage[IO]() {

    private val store = mutable.Map.empty[String, V]

    def get(key: K): IO[Option[V]] = IO.delay(store.get(Base16.encode(key)))

    def put(items: Seq[(K, V)]): IO[Unit] =
      IO.delay(items.foreach { case (k, v) => store.put(Base16.encode(k), v) })
  }
}
