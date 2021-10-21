package sttp.tapir.server.tests

import cats.implicits._
import sttp.client3._
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir._
import sttp.tapir.server.interceptor.attribute.AddLazyAttributeInterceptor
import sttp.tapir.tests.Test
import org.scalatest.matchers.should.Matchers._

// TODO: ServerAttributeTests?
class ServerSecurityTests[F[_], ROUTE](createServerTest: CreateServerTest[F, Any, ROUTE])(implicit
    m: MonadError[F]
) {
  import createServerTest._

  case class User(id: Int)

  def tests(): List[Test] = List(
    {
      val authInput = auth.bearer[String]()
      val errorOutput = statusCode(StatusCode.Unauthorized)
      val securityInterceptor =
        new AddLazyAttributeInterceptor(
          authInput,
          errorOutput,
          (key: String) => (if (key == "password") Right(User(123)) else Left(())).unit
        )
      val secureEndpoint: Endpoint[(String, User), Unit, String, Any] =
        endpoint.get.in("secret").in(authInput).in(extractRequiredAttribute[User]).out(stringBody)
      testServer(secureEndpoint, additionalInterceptors = List(securityInterceptor)) { case (_, u) =>
        pureResult(s"Hello, ${u.id}".asRight[Unit])
      } { (backend, baseUri) =>
        basicRequest.get(uri"$baseUri/secret").auth.bearer("password").send(backend).map(_.body shouldBe Right("Hello, 123")) *>
          basicRequest.get(uri"$baseUri/secret").auth.bearer("hacker").send(backend).map(_.code shouldBe StatusCode.Unauthorized) *>
          basicRequest.get(uri"$baseUri").auth.bearer("hacker").send(backend).map(_.code shouldBe StatusCode.NotFound)
      }
    }
  )
}
