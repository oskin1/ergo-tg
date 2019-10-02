package com.github.oskin1.wallet.models.storage

import com.github.oskin1.wallet.RawAddress
import org.ergoplatform.wallet.secrets.DerivationPath

/** Aggregates wallet account info.
  *
  * @param rawAddress - address identifier
  * @param derivationPath - account secret derivation path (private branch)
  */
final case class Account(
  rawAddress: RawAddress,
  derivationPath: DerivationPath
)
