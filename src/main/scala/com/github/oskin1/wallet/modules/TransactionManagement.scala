package com.github.oskin1.wallet.modules

import cats.implicits._
import cats.{Applicative, MonadError}
import com.github.oskin1.wallet.WalletError.NotEnoughBoxes
import com.github.oskin1.wallet.crypto.UnsafeMultiProver
import com.github.oskin1.wallet.models.PaymentRequest
import com.github.oskin1.wallet.models.network.Box
import org.ergoplatform.wallet.secrets.ExtendedSecretKey
import org.ergoplatform._
import scorex.crypto.authds.ADKey
import scorex.util.encode.Base16
import sigmastate.basics.DLogProtocol.DLogProverInput

trait TransactionManagement[F[_]] {

  private case class DecodedBox(id: ADKey, value: Long, sk: DLogProverInput)

  /** Take a number of outputs corresponding to a given requests and fee.
    *
    * @param outputs  - outputs zipped with a corresponding secrets
    * @param requests - transaction requests
    * @param fee      - fee amount
    */
  protected def collectOutputs(
    outputs: List[(Box, ExtendedSecretKey)],
    requests: List[PaymentRequest],
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
          MonadError[F, Throwable].raiseError(NotEnoughBoxes)
      }
    loop(List.empty, outputs, requests.map(_.amount).sum + fee)
  }

  /** Assemble and prove a new transaction.
    *
    * @param inputs        - inputs zipped with a corresponding secrets
    * @param requests      - transaction requests
    * @param fee           - fee amount
    * @param currentHeight - current blockchain height
   *  @param changeAddress - address to send change back
    */
  protected def makeTransaction(
    inputs: List[(Box, ExtendedSecretKey)],
    requests: List[PaymentRequest],
    fee: Long,
    currentHeight: Int,
    changeAddress: ErgoAddress
  )(implicit F: MonadError[F, Throwable]): F[ErgoLikeTransaction] =
    inputs
      .map {
        case (out, sk) =>
          Base16.decode(out.id).map(id => DecodedBox(ADKey @@ id, out.value, sk.key))
      }
      .sequence
      .map { decodedInputs =>
        val unsignedInputs =
          decodedInputs.map(x => new UnsignedInput(x.id)).toIndexedSeq
        val totalInput = decodedInputs.map(_.value).sum
        val totalOutput = requests.map(_.amount).sum
        val changeOutput = new ErgoBoxCandidate(
          totalInput - totalOutput - fee,
          changeAddress.script,
          currentHeight
        )
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
          outputs :+ feeOutput :+ changeOutput
        )
        UnsafeMultiProver.prove(unsignedTx, decodedInputs.map(x => x.id -> x.sk))
      }
      .fold[F[ErgoLikeTransaction]](
        e => MonadError[F, Throwable].raiseError(e),
        r => Applicative[F].pure(r)
      )
}
