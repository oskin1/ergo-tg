package com.github.oskin1.wallet.services

import cats.effect.Sync
import com.github.oskin1.wallet.models.network.{Balance, Transaction}
import com.github.oskin1.wallet.{ModifierId, RawAddress, Settings}
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}

/** Provides access to the Ergo network explorer.
  */
trait ExplorerService[F[_]] {

  /** Gets a transaction by its id from the network if it exists.
    */
  def getTransaction(id: ModifierId): F[Option[Transaction]]

  /** Gets balance of the given address from the network.
    */
  def getBalance(address: RawAddress): F[Balance]
}

object ExplorerService {

  final class Live[F[_]: Sync](client: Client[F], settings: Settings)
    extends ExplorerService[F] {

    def getTransaction(id: ModifierId): F[Option[Transaction]] =
      client.expectOption[Transaction](
        makeRequest(s"${settings.explorerUrl}/transactions/$id")
      )(jsonOf(Sync[F], Transaction.decoder))

    def getBalance(address: String): F[Balance] =
      client.expect[Balance](
        makeRequest(s"${settings.explorerUrl}/addresses/$address")
      )(jsonOf(Sync[F], Balance.decoder))

    private def makeRequest(uri: String) =
      Request[F](Method.GET, Uri.unsafeFromString(uri))
  }
}
