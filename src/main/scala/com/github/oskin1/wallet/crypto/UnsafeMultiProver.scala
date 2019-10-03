package com.github.oskin1.wallet.crypto

import org.ergoplatform.{
  ErgoLikeTransaction,
  Input,
  UnsignedErgoLikeTransaction
}
import scorex.crypto.authds.ADKey
import sigmastate.basics.DLogProtocol.DLogProverInput
import sigmastate.interpreter.{ContextExtension, ProverResult}

object UnsafeMultiProver {

  import org.ergoplatform.wallet.crypto.ErgoSignature._

  /**
    * Signs all inputs of a given `unsignedTx` with a corresponding sk.
    *
    * @note this method does not validate the cost of the given transaction
    * @return signed transaction
    */
  def prove(
    unsignedTx: UnsignedErgoLikeTransaction,
    inputs: List[(ADKey, DLogProverInput)]
  ): ErgoLikeTransaction = {
    val signedInputs = inputs.toIndexedSeq
      .map {
        case (boxId, sk) =>
          val sig = ProverResult(
            sign(unsignedTx.messageToSign, sk.w),
            ContextExtension.empty
          )
          Input(boxId, sig)
      }
    new ErgoLikeTransaction(
      signedInputs,
      unsignedTx.dataInputs,
      unsignedTx.outputCandidates
    )
  }
}
