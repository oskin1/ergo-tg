package com.github.oskin1.wallet.persistence

import java.io.File

import cats.effect.IO
import org.iq80.leveldb.Options
import org.scalacheck.Gen

trait StorageSpec {

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

  def withRealDb(fn: LDBStorage[IO] => Unit): Unit =
    fn(createRealDb(FileUtils.createTempDir.getAbsolutePath))

  def createRealDb(path: String): LDBStorage[IO] = {
    val dir = new File(path); dir.mkdirs()
    val options = new Options(); options.createIfMissing(true)
    val db = new LDBFactory {}.loadFactory[IO].unsafeRunSync().open(dir, options)
    new LDBStorage[IO](db)
  }

}
