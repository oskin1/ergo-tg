package com.github.oskin1.wallet.models

final case class NewWallet(rootAddress: String, mnemonic: String) {

  def verboseMsg: String =
    s"Your wallet was successfully created." +
    s"\nRoot address is: `$rootAddress`" +
    s"\nMnemonic phrase is: `$mnemonic`" +
    s"\n*It is strongly recommended to write down your" +
    s"mnemonic phrase and keep it in the safe place.*"
}
