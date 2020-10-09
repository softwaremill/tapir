package sttp.tapir.server.vertx

import io.vertx.scala.ext.web.{Route, Router}
import sttp.tapir.server.tests.ServerBasicTests

import scala.concurrent.Future

class VertxBlockingServerBasicTests extends VertxBlockingServerTests with ServerBasicTests[Future, Router => Route] {
  override def multipartInlineHeaderSupport: Boolean = false // README: doesn't seem supported but I may be wrong

  basicTests()
}
