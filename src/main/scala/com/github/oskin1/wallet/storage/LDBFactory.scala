package com.github.oskin1.wallet.storage

import org.iq80.leveldb.DBFactory

import scala.util.{Failure, Success, Try}

object LDBFactory {

  private val nativeFactory = "org.fusesource.leveldbjni.JniDBFactory"
  private val javaFactory   = "org.iq80.leveldb.impl.Iq80DBFactory"

  lazy val factory: Try[DBFactory] = {
    val loaders = List(ClassLoader.getSystemClassLoader, this.getClass.getClassLoader)
    val factories = List(nativeFactory, javaFactory)
    val pairs = loaders.view
      .zip(factories)
      .flatMap { case (loader, factoryName) =>
        Try(loader.loadClass(factoryName).getConstructor().newInstance().asInstanceOf[DBFactory]).toOption
      }

    pairs.headOption.fold[Try[DBFactory]](
      Failure(new Exception(s"Could not load any of the factory classes: $nativeFactory, $javaFactory"))
    )(Success(_))
  }

}

