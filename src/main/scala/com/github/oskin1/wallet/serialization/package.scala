package com.github.oskin1.wallet

import java.nio.charset.Charset

import com.google.common.base.Charsets
import com.google.common.primitives.Longs
import org.ergoplatform.wallet.secrets
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.ergoplatform.wallet.secrets.EncryptedSecret

import scala.util.Try

/** BytesEncoder/BytesDecoder instances for common types.
  */
package object serialization {

  private val charset: Charset = Charsets.UTF_8

  implicit val longsEncoder: BytesEncoder[Long] = Longs.toByteArray(_)

  implicit val longsDecoder: BytesDecoder[Long] = { xs =>
    Try(Longs.fromByteArray(xs)).fold(Left(_), Right(_))
  }

  implicit val secretBytesEncoder: BytesEncoder[EncryptedSecret] = {
    _.asJson.noSpaces.getBytes(charset)
  }

  implicit val secretBytesDecoder: BytesDecoder[EncryptedSecret] = { xs =>
    decode[secrets.EncryptedSecret](new String(xs, charset))
  }

}
