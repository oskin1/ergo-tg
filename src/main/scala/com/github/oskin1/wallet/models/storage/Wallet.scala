package com.github.oskin1.wallet.models.storage

import cats.data.NonEmptyList
import com.github.oskin1.wallet.RawAddress
import org.ergoplatform.wallet.secrets.EncryptedSecret

final case class Wallet(
  secret: EncryptedSecret,
  addresses: NonEmptyList[RawAddress]
)
