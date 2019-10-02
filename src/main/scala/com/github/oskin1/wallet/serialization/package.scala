package com.github.oskin1.wallet

import java.nio.charset.Charset

import com.github.oskin1.wallet.models.storage.Wallet
import com.google.common.base.Charsets
import com.google.common.primitives.{Ints, Longs}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.ergoplatform.wallet.secrets
import org.ergoplatform.wallet.secrets.EncryptedSecret

import scala.util.Try

/** BytesEncoder/BytesDecoder instances for common types.
  */
package object serialization {

  implicit final class BytesEncoderOps[A](wrappedVal: A) {

    def toBytes(implicit encoder: BytesEncoder[A]): Array[Byte] =
      encoder(wrappedVal)
  }

  implicit final class BytesDecoderOps(wrappedVal: Array[Byte]) {

    def as[T](implicit decoder: BytesDecoder[T]): Either[Throwable, T] =
      decoder(wrappedVal)
  }

  implicit val longsEncoder: BytesEncoder[Long] = Longs.toByteArray(_)

  implicit val longsDecoder: BytesDecoder[Long] = { xs =>
    Try(Longs.fromByteArray(xs)).fold(Left(_), Right(_))
  }

  implicit val intsEncoder: BytesEncoder[Int] = Ints.toByteArray(_)

  implicit val intsDecoder: BytesDecoder[Int] = { xs =>
    Try(Ints.fromByteArray(xs)).fold(Left(_), Right(_))
  }

  implicit val stringsEncoder: BytesEncoder[String] = _.getBytes(charset)

  implicit val stringsDecoder: BytesDecoder[String] = { xs =>
    Try(new String(xs, charset)).fold(Left(_), Right(_))
  }

  implicit val secretBytesEncoder: BytesEncoder[EncryptedSecret] = {
    _.asJson.noSpaces.getBytes(charset)
  }

  implicit val secretBytesDecoder: BytesDecoder[EncryptedSecret] = { xs =>
    decode[secrets.EncryptedSecret](new String(xs, charset))
  }

  implicit val walletBytesEncoder: BytesEncoder[Wallet] = {
    _.asJson.noSpaces.getBytes(charset)
  }

  implicit val walletBytesDecoder: BytesDecoder[Wallet] = { xs =>
    decode[Wallet](new String(xs, charset))
  }

  private def charset: Charset = Charsets.UTF_8

}
