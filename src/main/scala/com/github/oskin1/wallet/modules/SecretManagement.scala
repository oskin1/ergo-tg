package com.github.oskin1.wallet.modules

import cats.{Applicative, MonadError}
import com.github.oskin1.wallet.WalletError.AuthError
import com.github.oskin1.wallet.crypto.encryption
import com.github.oskin1.wallet.settings.Settings
import org.ergoplatform.wallet.mnemonic.Mnemonic
import org.ergoplatform.wallet.secrets.{DerivationPath, EncryptedSecret, ExtendedSecretKey}

trait SecretManagement[F[_]] {

  protected def deriveKey(
    rootSk: ExtendedSecretKey,
    derivationPath: DerivationPath
  ): ExtendedSecretKey =
    rootSk.derive(derivationPath).asInstanceOf[ExtendedSecretKey]

  protected def decryptSecret(
    secret: EncryptedSecret,
    pass: String
  )(implicit F: MonadError[F, Throwable]): F[Array[Byte]] =
    encryption
      .decrypt(secret, pass)
      .fold[F[Array[Byte]]](
        e =>
          MonadError[F, Throwable].raiseError(
            new Exception(AuthError(Option(e.getMessage)))
        ),
        r => Applicative[F].pure(r)
      )

  protected def generateMnemonic(
    settings: Settings
  )(implicit F: MonadError[F, Throwable]): F[String] = {
    val entropy =
      scorex.utils.Random.randomBytes(settings.seedStrengthBits / 8)
    new Mnemonic(settings.mnemonicPhraseLanguage, settings.seedStrengthBits)
      .toMnemonic(entropy)
      .fold(
        e => MonadError[F, Throwable].raiseError(e),
        r => Applicative[F].pure(r)
      )
  }
}
