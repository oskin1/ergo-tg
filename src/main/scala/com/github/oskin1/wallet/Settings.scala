package com.github.oskin1.wallet

import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.wallet.settings.EncryptionSettings

final case class Settings(
  storagePath: String,
  explorerUrl: String,
  networkType: String,
  seedStrengthBits: Int,
  mnemonicPhraseLanguage: String,
  encryption: EncryptionSettings
) {

  val addressEncoder: ErgoAddressEncoder = ErgoAddressEncoder(
    if (networkType == "mainnet") ErgoAddressEncoder.MainnetNetworkPrefix
    else ErgoAddressEncoder.TestnetNetworkPrefix
  )
}
