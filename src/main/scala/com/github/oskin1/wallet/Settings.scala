package com.github.oskin1.wallet

import org.ergoplatform.wallet.settings.EncryptionSettings

final case class Settings(
  storagePath: String,
  explorerUrl: String,
  addressPrefix: Byte,
  seedStrengthBits: Int,
  mnemonicPhraseLanguage: String,
  encryption: EncryptionSettings
)
