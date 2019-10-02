package com.github.oskin1.wallet.crypto

import com.github.oskin1.wallet.BoxId
import org.ergoplatform.{
  ErgoLikeTransaction,
  Input,
  UnsignedErgoLikeTransaction
}
import scorex.crypto.authds.ADKey
import scorex.util.encode.Base16
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
    inputs: List[(BoxId, DLogProverInput)]
  ): ErgoLikeTransaction = {
    val signedInputs = inputs.toIndexedSeq
      .flatMap {
        case (boxId, sk) =>
          Base16.decode(boxId).toOption.map(x => (ADKey @@ x) -> sk)
      }
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
