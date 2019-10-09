package com.github.oskin1.wallet.services

import cats.effect.IO
import cats.effect.concurrent.Ref
import com.github.oskin1.wallet.persistence.{TestStorage, UtxPool}
import com.github.oskin1.wallet.{repositories, services}
import com.github.oskin1.wallet.repositories.WalletRepo
import com.github.oskin1.wallet.settings.Settings
import org.ergoplatform.wallet.settings.EncryptionSettings
import org.scalatest.Matchers
import org.scalatest.propspec.AnyPropSpec

import scala.concurrent.duration._
import scala.util.Try

class WalletServiceSpec
  extends AnyPropSpec
  with Matchers
  with TestStorage
  with ExplorerServiceStub[IO] {

  val settings =
    Settings(
      "",
      "",
      "mainnet",
      0,
      1.second,
      128,
      "english",
      EncryptionSettings("HmacSHA256", 128000, 256)
    )

  val chatId = 1L

  val mnemonic =
    "about burst river test chief stadium true exile eye apple crew indoor black quantum banana"
  val rootAddress = "9hMe7DRRWLHPDXzy491xUaCf1fV1gJjf7FZMsr3fJ2dKtzxMMHx"
  val pass = "1234"

  val rootAddressWithMnemonicPass =
    "9h8p8QNc7pu98Ad4xrvezQsWjk1mek45xJ7e4qKDV8eJfaR9ps3"
  val mnemonicPass = "123456"

  def makeWalletRepo: WalletRepo[IO] =
    new repositories.WalletRepo.Live[IO](makeTestStorage)

  def makeUtxPoolRef: Ref[IO, UtxPool] =
    Ref.of[IO, UtxPool](UtxPool.empty).unsafeRunSync()

  def makeWalletService: WalletService[IO] =
    new services.WalletService.Live[IO](
      makeUtxPoolRef,
      makeExplorerService,
      makeWalletRepo,
      settings
    )

  property("exists") {
    val ws = makeWalletService

    ws.createWallet(chatId, pass).unsafeRunSync()

    ws.exists(chatId).unsafeRunSync() shouldBe true
    ws.exists(2L).unsafeRunSync() shouldBe false
  }

  property("restore wallet") {
    val ws = makeWalletService

    ws.restoreWallet(chatId, mnemonic, pass)
      .unsafeRunSync()
      .rootAddress shouldBe rootAddress

    ws.exists(chatId).unsafeRunSync() shouldBe true
  }

  property("restore wallet (with mnemonic pass)") {
    val ws = makeWalletService

    ws.restoreWallet(chatId, mnemonic, pass, Some(mnemonicPass))
      .unsafeRunSync()
      .rootAddress shouldBe rootAddressWithMnemonicPass

    ws.exists(chatId).unsafeRunSync() shouldBe true
  }

  property("create wallet") {
    val ws = makeWalletService

    ws.createWallet(chatId, pass).unsafeRunSync()
    ws.exists(chatId).unsafeRunSync() shouldBe true
  }

  property("create wallet (with mnemonic pass)") {
    val ws = makeWalletService

    ws.createWallet(chatId, pass, Some(mnemonicPass)).unsafeRunSync()
    ws.exists(chatId).unsafeRunSync() shouldBe true
  }
}
