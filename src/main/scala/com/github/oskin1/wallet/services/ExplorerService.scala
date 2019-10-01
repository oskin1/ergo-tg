package com.github.oskin1.wallet.services

import cats.effect.Sync
import com.github.oskin1.wallet.models.protocol.Transaction
import com.github.oskin1.wallet.{ModifierId, Settings}
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}

/** Provides access to the Ergo network explorer.
  */
trait ExplorerService[F[_]] {

  /** Gets transaction by its id from the network if it exists.
    */
  def getTransaction(id: ModifierId): F[Option[Transaction]]
}

object ExplorerService {

  final class Live[F[_]: Sync](client: Client[F], settings: Settings)
    extends ExplorerService[F] {

    def getTransaction(id: ModifierId): F[Option[Transaction]] =
      client.expectOption[Transaction](getTransactionRequest(id))(
        jsonOf(Sync[F], Transaction.decoder)
      )

    private def getTransactionUri(id: ModifierId) =
      Uri.unsafeFromString(s"${settings.explorerUrl}/transactions/$id")

    private def getTransactionRequest(id: ModifierId) =
      Request[F](Method.GET, getTransactionUri(id))
  }
}
