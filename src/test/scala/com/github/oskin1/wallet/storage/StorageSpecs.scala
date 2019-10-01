package com.github.oskin1.wallet.storage

import java.io.File

import org.iq80.leveldb.Options
import org.scalacheck.Gen
import zio.interop.catz._
import zio.{DefaultRuntime, Task}

trait StorageSpecs {

  val runtime: DefaultRuntime = new DefaultRuntime {}

  def testPairsGen: Gen[List[(Array[Byte], Array[Byte])]] =
    for {
      len  <- Gen.chooseNum(2, 256)
      str0 <- Gen.listOfN(len, Gen.alphaNumStr)
      str1 <- Gen.listOfN(len, Gen.alphaNumStr)
    } yield str0.map(toBytes).zip(str1.map(toBytes))

  def toBytes(s: String): Array[Byte] =
    org.iq80.leveldb.impl.Iq80DBFactory.bytes(s)

  def toString(xs: Array[Byte]): String =
    org.iq80.leveldb.impl.Iq80DBFactory.asString(xs)

  def withDb(fn: LDBStorage[Task] => Unit): Unit =
    fn(createDb(FileUtils.createTempDir.getAbsolutePath))

  def createDb(path: String): LDBStorage[Task] = {
    val dir = new File(path); dir.mkdirs()
    val options = new Options(); options.createIfMissing(true)
    val db =LDBFactory.factory.get.open(dir, options)
    new LDBStorage[Task](db)
  }

}
