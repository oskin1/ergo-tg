package com.github.oskin1.wallet.persistence

import cats.{Monad, MonadError}
import cats.implicits._
import com.github.oskin1.wallet.serialization._
import com.github.oskin1.wallet.serialization.{BytesDecoder, BytesEncoder}

/** Basic storage layer interface.
  */
abstract class Storage[F[_]: Monad](implicit F: MonadError[F, Throwable]) {

  type K = Array[Byte]
  type V = Array[Byte]

  def get(key: K): F[Option[V]]

  def put(items: Seq[(K, V)]): F[Unit]

  final def put(k: K, v: V): F[Unit] = put(Array(k -> v))

  /** Put typed key-value pair to the storage.
    */
  final def putT[KT, VT](k: KT, v: VT)(
    implicit
    ke: BytesEncoder[KT],
    ve: BytesEncoder[VT]
  ): F[Unit] =
    put(k.toBytes, v.toBytes)

  /** Get typed value by typed key from the storage.
    */
  final def getT[KT, VT](k: KT)(
    implicit
    ke: BytesEncoder[KT],
    vd: BytesDecoder[VT]
  ): F[Option[VT]] =
    get(k.toBytes)
      .map(_.map(_.as[VT]))
      .flatMap {
        case Some(Right(value)) => F.pure(Some(value))
        case Some(Left(e))      => F.raiseError(e)
        case None               => F.pure(None)
      }
}
