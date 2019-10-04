package com.github.oskin1.wallet.storage

import cats.{Applicative, MonadError}
import org.iq80.leveldb.DBFactory

import scala.util.Try

trait LDBFactory {

  private val nativeFactory = "org.fusesource.leveldbjni.JniDBFactory"
  private val javaFactory = "org.iq80.leveldb.impl.Iq80DBFactory"

  def loadFactory[F[_]](implicit F: MonadError[F, Throwable]): F[DBFactory] = {
    val loaders =
      List(ClassLoader.getSystemClassLoader, this.getClass.getClassLoader)
    val factories = List(nativeFactory, javaFactory)
    val pairs = loaders.view
      .zip(factories)
      .flatMap {
        case (loader, factoryName) =>
          Try(
            loader
              .loadClass(factoryName)
              .getConstructor()
              .newInstance()
              .asInstanceOf[DBFactory]
          ).toOption
      }

    pairs.headOption.fold[F[DBFactory]](
      F.raiseError(
        new Exception(
          s"Could not load any of the factory classes: $nativeFactory, $javaFactory"
        )
      )
    )(Applicative[F].pure(_))
  }

}
