package com.github.oskin1.wallet.modules

import org.log4s._

/** Provides logger to successors.
  */
trait Logging {

  protected lazy val log: Logger = getLogger
}
