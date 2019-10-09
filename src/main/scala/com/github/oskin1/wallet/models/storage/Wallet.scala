package com.github.oskin1.wallet.models.storage

import cats.data.NonEmptyList
import com.github.oskin1.wallet.RawAddress
import com.github.oskin1.wallet.crypto.encryption
import com.github.oskin1.wallet.models.storage
import com.github.oskin1.wallet.settings.Settings
import org.ergoplatform.wallet.mnemonic.Mnemonic
import org.ergoplatform.wallet.secrets.{EncryptedSecret, ExtendedSecretKey}
import org.ergoplatform.{ErgoAddressEncoder, P2PKAddress}

final case class Wallet(
  secret: EncryptedSecret,
  accounts: NonEmptyList[Account],
  changeAddress: RawAddress
)

object Wallet {

  /** Restore and instantiate root wallet from a given mnemonic phrase.
    */
  def rootWallet(
    mnemonic: String,
    pass: String,
    mnemonicPassOpt: Option[String]
  )(settings: Settings)(implicit e: ErgoAddressEncoder): Wallet = {
    val seed = Mnemonic.toSeed(mnemonic, mnemonicPassOpt)
    val secret = ExtendedSecretKey.deriveMasterKey(seed)
    val encryptedSecret = encryption.encrypt(seed, pass)(settings.encryption)
    val rootAddress = P2PKAddress(secret.publicKey.key).toString
    storage.Wallet(
      encryptedSecret,
      NonEmptyList(Account(rootAddress, secret.path), List.empty),
      rootAddress
    )
  }
}
