package com.github.oskin1.wallet.crypto

import org.ergoplatform.wallet.crypto
import org.ergoplatform.wallet.secrets.EncryptedSecret
import org.ergoplatform.wallet.settings.EncryptionSettings
import scorex.util.encode.Base16

import scala.util.Try

/** Crypto utilities.
  */
object encryption {

  def encrypt(secret: Array[Byte], pass: String)(
    settings: EncryptionSettings
  ): EncryptedSecret = {
    val iv = scorex.utils.Random.randomBytes(crypto.AES.NonceBitsLen / 8)
    val salt = scorex.utils.Random.randomBytes(32)
    val (ciphertext, tag) = crypto.AES.encrypt(secret, pass, salt, iv)(settings)
    EncryptedSecret(ciphertext, salt, iv, tag, settings)
  }

  def decrypt(
    encryptedSecret: EncryptedSecret,
    pass: String
  ): Try[Array[Byte]] =
    Base16
      .decode(encryptedSecret.cipherText)
      .flatMap { txt =>
        Base16
          .decode(encryptedSecret.salt)
          .flatMap { salt =>
            Base16
              .decode(encryptedSecret.iv)
              .flatMap { iv =>
                Base16
                  .decode(encryptedSecret.authTag)
                  .map((txt, salt, iv, _))
              }
          }
      }
      .flatMap {
        case (cipherText, salt, iv, tag) =>
          crypto.AES.decrypt(cipherText, pass, salt, iv, tag)(
            encryptedSecret.cipherParams
          )
      }

}
