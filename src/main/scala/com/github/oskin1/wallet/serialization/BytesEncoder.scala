package com.github.oskin1.wallet.serialization

/** A type providing conversion from some value of type `A` to byte array.
  */
trait BytesEncoder[-A] { self =>
  def apply(value: A): Array[Byte]
}
