package com.github.oskin1.wallet

import java.nio.charset.Charset

import cats.data.NonEmptyList
import com.google.common.base.Charsets
import com.google.common.primitives.{Bytes, Ints, Longs}
import org.ergoplatform.wallet.secrets
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import cats.implicits._
import com.github.oskin1.wallet.models.storage
import com.github.oskin1.wallet.models.storage.Wallet
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

  implicit val walletBytesEncoder: BytesEncoder[Wallet] = { obj =>
    val secretBytes = obj.secret.toBytes
    val secretBytesLen = secretBytes.length
    val addressesNum = obj.addresses.size
    val addressesBytes = obj.addresses
      .map(_.toBytes)
      .foldLeft(Array.empty[Byte])(Bytes.concat(_, _))
    Bytes.concat(
      secretBytesLen.toBytes,
      secretBytes,
      addressesNum.toBytes,
      addressesBytes
    )
  }

  implicit val walletBytesDecoder: BytesDecoder[Wallet] = { xs =>
    for {
      secretLen    <- xs.take(4).as[Int]
      secret       <- xs.slice(4, 4 + secretLen).as[EncryptedSecret]
      addressesNum <- xs.slice(4 + secretLen, 4 + secretLen + 4).as[Int]
      rem = xs.drop(4 + secretLen + 4)
      len = rem.length / addressesNum
      addresses <- rem.grouped(len).toList.map(_.as[RawAddress]).sequence
      nonEmptyAddresses <- NonEmptyList
                            .fromList(addresses)
                            .fold[Either[Throwable, NonEmptyList[RawAddress]]](
                              Left(new Exception("Empty addresses list"))
                            )(Right(_))
    } yield storage.Wallet(secret, nonEmptyAddresses)
  }

  private def charset: Charset = Charsets.UTF_8

}
