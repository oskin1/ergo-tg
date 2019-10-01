package com.github.oskin1.wallet.services

import canoe.models.ChatId
import cats.MonadError
import cats.effect.Sync
import cats.implicits._
import com.github.oskin1.wallet.models.{
  NewWallet,
  RestoredWallet,
  TransactionRequest
}
import com.github.oskin1.wallet.repos.SecretRepo
import com.github.oskin1.wallet.{encryption, Settings}
import org.ergoplatform.wallet.mnemonic.Mnemonic
import org.ergoplatform.wallet.secrets.{
  EncryptedSecret,
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

  def checkBalance(chatId: ChatId.Chat): F[Long]
}

object WalletService {

  final class Live[F[_]: Sync](
    explorerService: ExplorerService[F],
    secretRepo: SecretRepo[F],
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
      val (encryptedSecret, rootAddress) =
        deriveRootWallet(mnemonic, pass, mnemonicPassOpt)
      secretRepo.putSecret(chatId, encryptedSecret) *> Sync[F].delay(
        RestoredWallet(rootAddress.toString())
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
          val (encryptedSecret, rootAddress) =
            deriveRootWallet(mnemonic, pass, mnemonicPassOpt)
          secretRepo.putSecret(chatId, encryptedSecret) *> Sync[F].delay(
            NewWallet(rootAddress.toString(), mnemonic)
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

    def checkBalance(chatId: ChatId.Chat): F[Long] = ???

    private def deriveRootWallet(
      mnemonic: String,
      pass: String,
      mnemonicPassOpt: Option[String]
    ): (EncryptedSecret, P2PKAddress) = {
      val seed = Mnemonic.toSeed(mnemonic, mnemonicPassOpt)
      val secret = ExtendedSecretKey.deriveMasterKey(seed)
      val encryptedSecret =
        encryption.encrypt(ExtendedSecretKeySerializer.toBytes(secret), pass)(
          settings.encryption
        )
      val rootAddress = P2PKAddress(secret.publicKey.key)
      (encryptedSecret, rootAddress)
    }

  }
}
