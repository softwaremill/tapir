package sttp.tapir.examples.logging

import org.slf4j.{Logger, LoggerFactory}

/** Defines a [[org.slf4j.Logger]] instance `logger` named according to the class into which this trait is mixed.
  *
  * In a real-life project, you might rather want to use a macros-based SLF4J wrapper or logging backend.
  */
trait Logging {

  protected val logger: Logger = LoggerFactory.getLogger(getClass.getName)
}
