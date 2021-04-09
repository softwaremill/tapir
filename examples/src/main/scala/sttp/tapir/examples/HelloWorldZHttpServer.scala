package sttp.tapir.examples

import sttp.tapir._
import sttp.tapir.server.zhttp.ZHttpInterpreter
import zio._
import zhttp.service.Server

object HelloWorldZHttpServer extends App {

  val helloWorld: Endpoint[String, Throwable, String, Any] =
    endpoint
      .get
      .in("hello")
      .in(path[String]("name"))
      .errorOut(plainBody[String].map(err => new Throwable(err))(_.getMessage))
      .out(stringBody)

  val app = ZHttpInterpreter.convert(helloWorld)(name => ZIO.succeed(s"Hello $name"))



  // starting the server
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
