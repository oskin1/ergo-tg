package com.github.oskin1.wallet.services

import cats.Applicative
import com.github.oskin1.wallet.RawAddress
import com.github.oskin1.wallet.models.network.{
  Balance,
  BlockchainInfo,
  Box,
  Transaction
}
import org.ergoplatform.ErgoLikeTransaction

trait ExplorerServiceStub[F[_]] {

  def makeExplorerService(implicit F: Applicative[F]): ExplorerService[F] =
    new ExplorerService[F] {

      def getBalance(address: RawAddress): F[Balance] =
        Applicative[F].pure(Balance.empty)

      def getBlockchainInfo: F[BlockchainInfo] =
        Applicative[F].pure(BlockchainInfo(height = 1))

      def getTransactionsSince(height: Int): F[List[Transaction]] =
        Applicative[F].pure(List.empty)

      def getUnspentOutputs(address: RawAddress): F[List[Box]] =
        Applicative[F].pure(List.empty)

      def submitTransaction(tx: ErgoLikeTransaction): F[String] =
        Applicative[F].pure("")
    }
}
