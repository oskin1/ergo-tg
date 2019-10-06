package com.github.oskin1.wallet.settings

import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.wallet.settings.EncryptionSettings

import scala.concurrent.duration.FiniteDuration

final case class Settings(
  storagePath: String,
  explorerUrl: String,
  networkType: String,
  minConfirmationsNum: Int,
  explorerPollingInterval: FiniteDuration,
  seedStrengthBits: Int,
  mnemonicPhraseLanguage: String,
  encryption: EncryptionSettings
) {

  val addressEncoder: ErgoAddressEncoder = ErgoAddressEncoder(
    if (networkType == "mainnet") ErgoAddressEncoder.MainnetNetworkPrefix
    else ErgoAddressEncoder.TestnetNetworkPrefix
  )
}
