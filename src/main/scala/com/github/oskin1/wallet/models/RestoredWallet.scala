package com.github.oskin1.wallet.models

final case class RestoredWallet(rootAddress: String) {

  def verboseMsg: String =
    s"Your wallet was successfully restored." +
    s"\nRoot address is: $rootAddress"
}
