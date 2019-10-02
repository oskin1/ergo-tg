package com.github.oskin1.wallet.services

import canoe.models.ChatId
import cats.MonadError
import cats.data.NonEmptyList
import cats.effect.Sync
import cats.implicits._
import com.github.oskin1.wallet.models.network.Balance
import com.github.oskin1.wallet.models.storage.Wallet
import com.github.oskin1.wallet.models.{
  storage,
  NewWallet,
  RestoredWallet,
  TransactionRequest
}
import com.github.oskin1.wallet.repos.WalletRepo
import com.github.oskin1.wallet.{encryption, Settings}
import org.ergoplatform.wallet.mnemonic.Mnemonic
import org.ergoplatform.wallet.secrets.{
  ExtendedSecretKey,
  ExtendedSecretKeySerializer
}
import org.ergoplatform.{ErgoAddressEncoder, P2PKAddress}

/** Provides actual wallet functionality.
  */
trait WalletService[F[_]] {

  def restoreWallet(
    chatId: ChatId.Chat,
    mnemonic: String,
    pass: String,
    mnemonicPassOpt: Option[String]
  ): F[RestoredWallet]

  def createWallet(
    chatId: ChatId.Chat,
    pass: String,
    mnemonicPassOpt: Option[String],
  ): F[NewWallet]

  def createTransaction(
    chatId: ChatId.Chat,
    pass: String,
    requests: Seq[TransactionRequest],
    fee: Long
  ): F[String]

  def getBalance(chatId: ChatId.Chat): F[Balance]
}

object WalletService {

  final class Live[F[_]: Sync](
    explorerService: ExplorerService[F],
    walletRepo: WalletRepo[F],
    settings: Settings
  ) extends WalletService[F] {

    implicit private val addressEncoder: ErgoAddressEncoder =
      ErgoAddressEncoder(settings.addressPrefix)

    def restoreWallet(
      chatId: ChatId.Chat,
      mnemonic: String,
      pass: String,
      mnemonicPassOpt: Option[String],
    ): F[RestoredWallet] = {
      val wallet = deriveRootWallet(mnemonic, pass, mnemonicPassOpt)
      walletRepo.putWallet(chatId, wallet) *> Sync[F].delay(
        RestoredWallet(wallet.addresses.head)
      )
    }

    def createWallet(
      chatId: ChatId.Chat,
      pass: String,
      mnemonicPassOpt: Option[String]
    ): F[NewWallet] = {
      val entropy =
        scorex.utils.Random.randomBytes(settings.seedStrengthBits / 8)
      new Mnemonic(settings.mnemonicPhraseLanguage, settings.seedStrengthBits)
        .toMnemonic(entropy)
        .map { mnemonic =>
          val wallet = deriveRootWallet(mnemonic, pass, mnemonicPassOpt)
          walletRepo.putWallet(chatId, wallet) *> Sync[F].delay(
            NewWallet(wallet.addresses.head, mnemonic)
          )
        }
        .fold(e => MonadError[F, Throwable].raiseError(e), r => r)
    }

    def createTransaction(
      chatId: ChatId.Chat,
      pass: String,
      requests: Seq[TransactionRequest],
      fee: Long
    ): F[String] = ???

    def getBalance(chatId: ChatId.Chat): F[Balance] =
      walletRepo.readWallet(chatId).flatMap {
        _.fold(Balance.empty.pure) { wallet =>
          wallet.addresses
            .map(explorerService.getBalance)
            .sequence
            .map(_.reduce(_ merge _))
        }
      }

    private def deriveRootWallet(
      mnemonic: String,
      pass: String,
      mnemonicPassOpt: Option[String]
    ): Wallet = {
      val seed = Mnemonic.toSeed(mnemonic, mnemonicPassOpt)
      val secret = ExtendedSecretKey.deriveMasterKey(seed)
      val encryptedSecret =
        encryption.encrypt(ExtendedSecretKeySerializer.toBytes(secret), pass)(
          settings.encryption
        )
      val rootAddress = P2PKAddress(secret.publicKey.key)
      storage.Wallet(
        encryptedSecret,
        NonEmptyList(rootAddress.toString(), List.empty)
      )
    }
  }
}
