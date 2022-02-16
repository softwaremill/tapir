package sttp.tapir.server.stub

import sttp.tapir._
import sttp.tapir.server.ServerEndpoint.Full

import scala.concurrent.Future

object DocsExample {

  val greetUser: Full[String, String, Unit, String, String, Any, Future] = endpoint.get
    .in("api" / "users" / "greet")
    .securityIn(auth.bearer[String]())
    .out(stringBody)
    .errorOut(stringBody)
    .serverSecurityLogic(token => Future.successful {
      if (token == "secret-password") Right("user123") else Left("unauthorized")
    })
    .serverLogic(user => _ => Future.successful(Right(s"hello $user")))

}
