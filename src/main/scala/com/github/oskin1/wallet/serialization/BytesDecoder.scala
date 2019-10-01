package com.github.oskin1.wallet.serialization

/** A type providing conversion from byte array to some value of type `A`.
 */
trait BytesDecoder[A] { self =>
  def apply(xs: Array[Byte]): Either[Throwable, A]
}
