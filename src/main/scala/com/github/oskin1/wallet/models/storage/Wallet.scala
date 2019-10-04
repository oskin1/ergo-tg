package com.github.oskin1.wallet.models.storage

import cats.data.NonEmptyList
import com.github.oskin1.wallet.Settings
import com.github.oskin1.wallet.crypto.encryption
import com.github.oskin1.wallet.models.storage
import org.ergoplatform.{ErgoAddressEncoder, P2PKAddress}
import org.ergoplatform.wallet.mnemonic.Mnemonic
import org.ergoplatform.wallet.secrets.{
  EncryptedSecret,
  ExtendedSecretKey,
  ExtendedSecretKeySerializer
}

final case class Wallet(
  secret: EncryptedSecret,
  accounts: NonEmptyList[Account]
)

object Wallet {

  def rootWallet(
    mnemonic: String,
    pass: String,
    mnemonicPassOpt: Option[String]
  )(settings: Settings)(implicit e: ErgoAddressEncoder): Wallet = {
    val seed = Mnemonic.toSeed(mnemonic, mnemonicPassOpt)
    val secret = ExtendedSecretKey.deriveMasterKey(seed)
    val encryptedSecret =
      encryption.encrypt(ExtendedSecretKeySerializer.toBytes(secret), pass)(
        settings.encryption
      )
    val rootAddress = P2PKAddress(secret.publicKey.key)
    storage.Wallet(
      encryptedSecret,
      NonEmptyList(Account(rootAddress.toString(), secret.path), List.empty)
    )
  }
}
