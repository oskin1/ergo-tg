package com.github.oskin1.wallet.storage

import java.io.IOException
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.nio.file.attribute.BasicFileAttributes

import org.scalacheck.Gen

object FileUtils {

  protected val randomPrefixLength = 10

  val basePath: Path = java.nio.file.Files.createTempDirectory(s"test-${System.nanoTime()}")

  sys.addShutdownHook {
    remove(basePath)
  }

  def createTempFile: java.io.File = {
    val dir = createTempDir
    val prefix = scala.util.Random.alphanumeric.take(randomPrefixLength).mkString
    val suffix = scala.util.Random.alphanumeric.take(randomPrefixLength).mkString
    val file = java.nio.file.Files.createTempFile(dir.toPath, prefix, suffix).toFile
    file.deleteOnExit()
    file
  }

  def createTempDir: java.io.File = {
    val rndString = scala.util.Random.alphanumeric.take(randomPrefixLength).mkString
    createTempDirForPrefix(rndString)
  }

  def tempDirGen: Gen[java.io.File] = Gen.listOfN(randomPrefixLength, Gen.alphaNumChar).map { p =>
    val prefix = p.mkString("")
    createTempDirForPrefix(prefix)
  }

  /**
   * Recursively remove all files and directories in `root`
   */
  def remove(root: Path): Unit = {
    Files.walkFileTree(root, new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        Files.delete(file)
        FileVisitResult.CONTINUE
      }

      override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
        Files.delete(dir)
        FileVisitResult.CONTINUE
      }
    })
  }

  private def createTempDirForPrefix(prefix: String): java.io.File = {
    val file = java.nio.file.Files.createTempDirectory(basePath, prefix).toFile
    file.deleteOnExit()
    file
  }

}