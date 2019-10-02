package com.github.oskin1.wallet.services

import cats.effect.Sync
import com.github.oskin1.wallet.models.network.{Balance, Output, Transaction}
import com.github.oskin1.wallet.{ModifierId, RawAddress, Settings}
import io.circe.Decoder
import org.ergoplatform.ErgoLikeTransaction
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}

/** Provides access to the Ergo network explorer.
  */
trait ExplorerService[F[_]] {

  /** Get a transaction by its id from the network if it exists.
    */
  def getTransaction(id: ModifierId): F[Option[Transaction]]

  /** Get balance of the given address from the network.
    */
  def getBalance(address: RawAddress): F[Balance]

  /** Get unspent outputs of the given address from the network.
    */
  def getUnspentOutputs(address: RawAddress): F[List[Output]]

  /** Get current height of the latest block in the network.
    */
  def getCurrentHeight: F[Int]

  /** Submit transaction to the network.
    */
  def submitTransaction(tx: ErgoLikeTransaction): F[String]
}

object ExplorerService {

  final class Live[F[_]: Sync](client: Client[F], settings: Settings)
    extends ExplorerService[F] {

    def getTransaction(id: ModifierId): F[Option[Transaction]] =
      client.expectOption[Transaction](
        makeRequest(s"${settings.explorerUrl}/transactions/$id")
      )(jsonOf(Sync[F], implicitly[Decoder[Transaction]]))

    def getBalance(address: String): F[Balance] =
      client.expect[Balance](
        makeRequest(s"${settings.explorerUrl}/addresses/$address")
      )(jsonOf(Sync[F], implicitly[Decoder[Balance]]))

    def getUnspentOutputs(address: RawAddress): F[List[Output]] =
      client.expect[List[Output]](
        makeRequest(
          s"${settings.explorerUrl}/transactions/boxes/byAddress/unspent/$address"
        )
      )(jsonOf(Sync[F], implicitly[Decoder[List[Output]]]))

    def getCurrentHeight: F[Int] = ???

    def submitTransaction(tx: ErgoLikeTransaction): F[String] = ???

    private def makeRequest(uri: String) =
      Request[F](Method.GET, Uri.unsafeFromString(uri))
  }
}
