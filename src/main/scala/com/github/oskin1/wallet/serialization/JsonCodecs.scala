package com.github.oskin1.wallet.serialization

import java.math.BigInteger

import io.circe._
import io.circe.syntax._
import org.ergoplatform.ErgoBox.{NonMandatoryRegisterId, TokenId}
import org.ergoplatform._
import org.ergoplatform.settings.ErgoAlgos
import scorex.crypto.authds.{ADDigest, ADKey}
import scorex.crypto.hash.Digest32
import scorex.util.ModifierId
import sigmastate.Values.{ErgoTree, EvaluatedValue}
import sigmastate.eval.{SigmaDsl, WrapperOf}
import sigmastate.interpreter.{ContextExtension, ProverResult}
import sigmastate.serialization.{ErgoTreeSerializer, ValueSerializer}
import sigmastate.{AvlTreeData, SType}
import special.collection.Coll
import special.sigma.{Header, PreHeader}

object JsonCodecs {

  implicit val sigmaBigIntEncoder: Encoder[special.sigma.BigInt] = { bigInt =>
    JsonNumber
      .fromDecimalStringUnsafe(
        bigInt.asInstanceOf[WrapperOf[BigInteger]].wrappedValue.toString
      )
      .asJson
  }

  implicit val arrayBytesEncoder: Encoder[Array[Byte]] =
    ErgoAlgos.encode(_).asJson

  implicit val collBytesEncoder: Encoder[Coll[Byte]] =
    ErgoAlgos.encode(_).asJson

  implicit val adKeyEncoder: Encoder[ADKey] = _.array.asJson

  implicit val adDigestEncoder: Encoder[ADDigest] = _.array.asJson

  implicit val digest32Encoder: Encoder[Digest32] = _.array.asJson

  implicit val assetEncoder: Encoder[(TokenId, Long)] = { asset =>
    Json.obj(
      "tokenId" -> asset._1.asJson,
      "amount"  -> asset._2.asJson
    )
  }

  implicit val modifierIdEncoder: Encoder[ModifierId] =
    _.asInstanceOf[String].asJson

  implicit val headerEncoder: Encoder[Header] = { h: Header =>
    Map(
      "id"               -> h.id.asJson,
      "version"          -> h.version.asJson,
      "parentId"         -> h.parentId.asJson,
      "adProofsRoot"     -> h.ADProofsRoot.asJson,
      "stateRoot"        -> SigmaDsl.toAvlTreeData(h.stateRoot).asJson,
      "transactionsRoot" -> h.transactionsRoot.asJson,
      "timestamp"        -> h.timestamp.asJson,
      "nBits"            -> h.nBits.asJson,
      "height"           -> h.height.asJson,
      "extensionRoot"    -> h.extensionRoot.asJson,
      "minerPk"          -> h.minerPk.getEncoded.asJson,
      "powOnetimePk"     -> h.powOnetimePk.getEncoded.asJson,
      "powNonce"         -> h.powNonce.asJson,
      "powDistance"      -> h.powDistance.asJson,
      "votes"            -> h.votes.asJson
    ).asJson
  }

  implicit val preHeaderEncoder: Encoder[PreHeader] = { v: PreHeader =>
    Map(
      "version"   -> v.version.asJson,
      "parentId"  -> v.parentId.asJson,
      "timestamp" -> v.timestamp.asJson,
      "nBits"     -> v.nBits.asJson,
      "height"    -> v.height.asJson,
      "minerPk"   -> v.minerPk.getEncoded.asJson,
      "votes"     -> v.votes.asJson
    ).asJson
  }

  implicit val evaluatedValueEncoder: Encoder[EvaluatedValue[_ <: SType]] = {
    value =>
      ValueSerializer.serialize(value).asJson
  }

  implicit val dataInputEncoder: Encoder[DataInput] = { input =>
    Json.obj(
      "boxId" -> input.boxId.asJson,
    )
  }

  implicit val inputEncoder: Encoder[Input] = { input =>
    Json.obj(
      "boxId"         -> input.boxId.asJson,
      "spendingProof" -> input.spendingProof.asJson
    )
  }

  implicit val unsignedInputEncoder: Encoder[UnsignedInput] = { input =>
    Json.obj(
      "boxId"     -> input.boxId.asJson,
      "extension" -> input.extension.asJson
    )
  }

  implicit val contextExtensionEncoder: Encoder[ContextExtension] = {
    extension =>
      extension.values.map {
        case (key, value) =>
          key -> evaluatedValueEncoder(value)
      }.asJson
  }

  implicit val proverResultEncoder: Encoder[ProverResult] = { v =>
    Json.obj(
      "proofBytes" -> v.proof.asJson,
      "extension"  -> v.extension.asJson
    )
  }

  implicit val avlTreeDataEncoder: Encoder[AvlTreeData] = { v =>
    Json.obj(
      "digest"      -> v.digest.asJson,
      "treeFlags"   -> v.treeFlags.serializeToByte.asJson,
      "keyLength"   -> v.keyLength.asJson,
      "valueLength" -> v.valueLengthOpt.asJson
    )
  }

  implicit val ergoTreeEncoder: Encoder[ErgoTree] = { value =>
    ErgoTreeSerializer.DefaultSerializer.serializeErgoTree(value).asJson
  }

  implicit def registersEncoder[T <: EvaluatedValue[_ <: SType]]
    : Encoder[Map[NonMandatoryRegisterId, T]] = { m =>
    Json.obj(
      m.toSeq
        .sortBy(_._1.number)
        .map { case (k, v) => k.toString() -> evaluatedValueEncoder(v) }: _*
    )
  }

  implicit val ergoBoxEncoder: Encoder[ErgoBox] = { box =>
    Json.obj(
      "boxId" -> box.id.asJson,
      "value" -> box.value.asJson,
      "ergoTree" -> ErgoTreeSerializer.DefaultSerializer
        .serializeErgoTree(box.ergoTree)
        .asJson,
      "assets"              -> box.additionalTokens.toArray.toSeq.asJson,
      "creationHeight"      -> box.creationHeight.asJson,
      "additionalRegisters" -> box.additionalRegisters.asJson,
      "transactionId"       -> box.transactionId.asJson,
      "index"               -> box.index.asJson
    )
  }

  implicit val ergoLikeTransactionEncoder: Encoder[ErgoLikeTransaction] = {
    tx =>
      Json.obj(
        "id"         -> tx.id.asJson,
        "inputs"     -> tx.inputs.asJson,
        "dataInputs" -> tx.dataInputs.asJson,
        "outputs"    -> tx.outputs.asJson
      )
  }

  val txIdDecoder: Decoder[String] = { c =>
    c.downField("id").as[String].map(_.stripPrefix("\"").stripSuffix("\""))
  }
}
