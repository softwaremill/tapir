package tapir.example.zio

import tapir._
import tapir.server.http4s._
import org.http4s._
import org.http4s.syntax.kleisli._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import scalaz.zio.{DefaultRuntime, IO, Task, UIO}
import scalaz.zio.interop.catz._
import scalaz.zio.interop.catz.implicits._

object ZioExample extends App {

  implicit class ZioEndpoint[I, E, O](e: Endpoint[I, E, O, EntityBody[Task]]) {
    def toZioRoutes(logic: I => IO[E, O])(implicit serverOptions: Http4sServerOptions[Task]): HttpRoutes[Task] = {
      import tapir.server.http4s._
      e.toRoutes(i => logic(i).either)
    }
  }

  def countCharacters(s: String): UIO[Int] = UIO.succeed(s.length)

  val countCharactersEndpoint: Endpoint[String, Unit, Int, Nothing] = endpoint.in(stringBody).out(plainBody[Int])
  def service: HttpRoutes[Task] = countCharactersEndpoint.toZioRoutes(countCharacters _)

  {
    implicit val runtime: DefaultRuntime = new DefaultRuntime {}

    val serve = BlazeServerBuilder[Task]
      .bindHttp(8080, "localhost")
      .withHttpApp(Router("/" -> service).orNotFound)
      .serve
      .compile
      .drain

    runtime.unsafeRun(serve)
  }
}
