package sttp.tapir.tests

import com.typesafe.scalalogging.StrictLogging

object PortCounter extends StrictLogging {
  val DefaultStartPort: Port = 5000

  private var _start: Port = DefaultStartPort
  private var _next: Port = _start

  // startWith() might be called concurrently by many tests in a single subproject. We want to initialise with the
  // port read from config only once.
  def startWith(p: Port): Unit = this.synchronized {
    if (p != _start) {
      _start = p
      _next = _start
      logger.info(s"Starting with port: ${_start}")
    }
  }

  def next(): Port = this.synchronized {
    val r = _next
    _next = _next + 1
    logger.info(s"Next port: $r")
    r
  }
}
