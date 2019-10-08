package com.github.oskin1.wallet.repositories

import cats.data.NonEmptyList
import cats.effect.IO
import com.github.oskin1.wallet.models.storage.{Account, Wallet}
import com.github.oskin1.wallet.persistence.TestStorage
import com.github.oskin1.wallet.repositories
import org.ergoplatform.wallet.secrets.{DerivationPath, EncryptedSecret}
import org.ergoplatform.wallet.settings.EncryptionSettings
import org.scalatest.Matchers
import org.scalatest.propspec.AnyPropSpec

class WalletRepoSpec extends AnyPropSpec with Matchers with TestStorage {

  val chatId = 1L

  val wallet = Wallet(
    EncryptedSecret("x", "x", "x", "x", EncryptionSettings("x", 0, 0)),
    NonEmptyList(
      Account("x", DerivationPath.fromEncoded("m/1").get),
      List.empty
    ),
    "x"
  )

  def makeWalletRepo: WalletRepo[IO] =
    new repositories.WalletRepo.Live[IO](makeTestStorage)

  property("putWallet/readWallet") {
    val wr = makeWalletRepo
    wr.putWallet(chatId, wallet).unsafeRunSync()
    wr.readWallet(chatId).unsafeRunSync() shouldBe Some(wallet)
  }
}
