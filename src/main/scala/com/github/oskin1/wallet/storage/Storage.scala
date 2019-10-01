package com.github.oskin1.wallet.storage

/** Basic storage layer interface.
  */
abstract class Storage[F[_]] {

  type K = Array[Byte]
  type V = Array[Byte]

  def get(key: K): F[Option[V]]

  def put(items: Seq[(K, V)]): F[Unit]
}
