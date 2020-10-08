package sttp.tapir.server.play

import play.api.routing.Router
import sttp.tapir.server.tests.ServerBasicTests
import sttp.tapir.tests.PortCounter

import scala.concurrent.Future

class PlayServerBasicTests extends PlayServerTests with ServerBasicTests[Future, Router.Routes] {
  override def multipleValueHeaderSupport: Boolean = false
  override def multipartInlineHeaderSupport: Boolean = false
  override def inputStreamSupport: Boolean = false

  basicTests()

  override val portCounter: PortCounter = new PortCounter(43000)
}
