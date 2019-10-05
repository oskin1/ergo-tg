package com.github.oskin1.wallet.services

import cats.effect.Sync
import com.github.oskin1.wallet.models.network.{
  Balance,
  BlockchainInfo,
  Box,
  Transaction
}
import com.github.oskin1.wallet.{ModifierId, RawAddress, Settings}
import io.circe.Decoder
import org.ergoplatform.{ErgoLikeTransaction, JsonCodecs}
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}
import org.http4s.circe.{jsonEncoderOf, jsonOf}

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
  def getUnspentOutputs(address: RawAddress): F[List[Box]]

  /** Get current height of the latest block in the network.
    */
  def getBlockchainInfo: F[BlockchainInfo]

  /** Submit a transaction to the network.
    */
  def submitTransaction(tx: ErgoLikeTransaction): F[String]
}

object ExplorerService {

  final class Live[F[_]: Sync](client: Client[F], settings: Settings)
    extends ExplorerService[F] {

    private val codecs = new JsonCodecs {}

    def getTransaction(id: ModifierId): F[Option[Transaction]] =
      client.expectOption[Transaction](
        makeGetRequest(s"${settings.explorerUrl}/transactions/$id")
      )(jsonOf(Sync[F], implicitly[Decoder[Transaction]]))

    def getBalance(address: String): F[Balance] =
      client.expect[Balance](
        makeGetRequest(s"${settings.explorerUrl}/addresses/$address")
      )(jsonOf(Sync[F], implicitly[Decoder[Balance]]))

    def getUnspentOutputs(address: RawAddress): F[List[Box]] =
      client.expect[List[Box]](
        makeGetRequest(
          s"${settings.explorerUrl}/transactions/boxes/byAddress/unspent/$address"
        )
      )(jsonOf(Sync[F], implicitly[Decoder[List[Box]]]))

    def getBlockchainInfo: F[BlockchainInfo] =
      client.expect[BlockchainInfo](
        makeGetRequest(
          s"${settings.explorerUrl}/blocks?offset=0&limit=1&sortDirection=DESC"
        )
      )(jsonOf(Sync[F], implicitly[Decoder[BlockchainInfo]]))

    def submitTransaction(tx: ErgoLikeTransaction): F[String] =
      client.expect[String](
        Request[F](
          Method.POST,
          Uri.unsafeFromString(s"${settings.explorerUrl}/transactions")
        ).withEntity(tx)(jsonEncoderOf(Sync[F], codecs.ergoLikeTransactionEncoder))
      )

    private def makeGetRequest(uri: String) =
      Request[F](Method.GET, Uri.unsafeFromString(uri))
  }
}
