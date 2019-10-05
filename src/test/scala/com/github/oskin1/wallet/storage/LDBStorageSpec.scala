package com.github.oskin1.wallet.storage

import cats.implicits._
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks
import org.scalatest.propspec.AnyPropSpec
import zio.interop.catz._

class LDBStorageSpec
  extends AnyPropSpec
    with Matchers
    with PropertyChecks
    with StorageSpecs {

  property("put/get") {
    withDb { db =>
      val valueA = (toBytes("A"), toBytes("1"))
      val valueB = (toBytes("B"), toBytes("2"))
      val values = List(valueA, valueB)

      runtime.unsafeRun(db.put(values))

      runtime.unsafeRun(
        values.map(_._1).map(db.get).sequence).map(_.map(toString)
      ) should contain theSameElementsAs values.map(x => Some(toString(x._2)))
    }
  }

}
