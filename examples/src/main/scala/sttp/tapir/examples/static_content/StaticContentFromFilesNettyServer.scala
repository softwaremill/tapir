package sttp.tapir.examples.static_content

import sttp.tapir.server.netty.NettyFutureServer
import sttp.tapir.{emptyInput, filesServerEndpoints}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object StaticContentFromFilesNettyServer extends App {
  NettyFutureServer()
    .port(8080)
    .addEndpoints(filesServerEndpoints[Future](emptyInput)("/var/www"))
    .start()
    .flatMap(_ => Future.never)
}
