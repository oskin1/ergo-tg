package com.github.oskin1.wallet.modules

import cats.implicits._
import cats.{Applicative, MonadError}
import com.github.oskin1.wallet.crypto.UnsafeMultiProver
import com.github.oskin1.wallet.models.TransactionRequest
import com.github.oskin1.wallet.models.network.Box
import org.ergoplatform.{
  ErgoBoxCandidate,
  ErgoLikeTransaction,
  ErgoScriptPredef,
  UnsignedErgoLikeTransaction,
  UnsignedInput
}
import org.ergoplatform.wallet.secrets.ExtendedSecretKey
import scorex.crypto.authds.ADKey
import scorex.util.encode.Base16

trait TransactionManagement[F[_]] {

  /** Take a number of outputs corresponding to a given requests and fee.
    *
    * @param outputs  - outputs zipped with a corresponding secrets
    * @param requests - transaction requests
    * @param fee      - fee amount
    */
  protected def collectOutputs(
    outputs: List[(Box, ExtendedSecretKey)],
    requests: List[TransactionRequest],
    fee: Long
  )(
    implicit F: MonadError[F, Throwable]
  ): F[List[(Box, ExtendedSecretKey)]] = {
    @scala.annotation.tailrec
    def loop(
      acc: List[(Box, ExtendedSecretKey)],
      rem: List[(Box, ExtendedSecretKey)],
      amtRem: Long
    ): F[List[(Box, ExtendedSecretKey)]] =
      rem match {
        case head :: tail if amtRem > 0 =>
          loop(acc :+ head, tail, amtRem - head._1.value)
        case _ if amtRem <= 0 =>
          Applicative[F].pure(acc)
        case _ =>
          MonadError[F, Throwable].raiseError(
            new Exception("Not enough boxes")
          )
      }
    loop(List.empty, outputs, requests.map(_.amount).sum + fee)
  }

  /** Assemble and prove a new transaction.
    *
    * @param inputs        - inputs zipped with a corresponding secrets
    * @param requests      - transaction requests
    * @param fee           - fee amount
    * @param currentHeight - current blockchain height
    */
  protected def makeTransaction(
    inputs: List[(Box, ExtendedSecretKey)],
    requests: List[TransactionRequest],
    fee: Long,
    currentHeight: Int
  )(implicit F: MonadError[F, Throwable]): F[ErgoLikeTransaction] =
    inputs
      .map {
        case (out, sk) =>
          Base16.decode(out.id).map(id => (ADKey @@ id, sk.key))
      }
      .sequence
      .map { decodedInputs =>
        val unsignedInputs =
          decodedInputs.map(x => new UnsignedInput(x._1)).toIndexedSeq
        val feeOutput = new ErgoBoxCandidate(
          fee,
          ErgoScriptPredef.feeProposition(),
          currentHeight
        )
        val outputs = requests.map { req =>
          new ErgoBoxCandidate(req.amount, req.address.script, currentHeight)
        }.toIndexedSeq
        val unsignedTx = new UnsignedErgoLikeTransaction(
          unsignedInputs,
          IndexedSeq.empty,
          outputs :+ feeOutput
        )
        UnsafeMultiProver.prove(unsignedTx, decodedInputs)
      }
      .fold[F[ErgoLikeTransaction]](
        e => MonadError[F, Throwable].raiseError(e),
        r => Applicative[F].pure(r)
      )
}
