package sttp.tapir.tests

import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.scalalogging.StrictLogging

class PortCounter(initial: Int) extends StrictLogging {
  private val _next = new AtomicInteger(initial)
  def next(): Port = {
    val r = _next.getAndIncrement()
    logger.info(s"Next port: $r")
    r
  }
}
