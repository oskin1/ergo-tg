package com.github.oskin1.wallet.models.storage

import cats.data.NonEmptyList
import org.ergoplatform.wallet.secrets.EncryptedSecret

final case class Wallet(
  secret: EncryptedSecret,
  accounts: NonEmptyList[Account]
)
