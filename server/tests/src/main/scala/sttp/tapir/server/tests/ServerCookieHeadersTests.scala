package sttp.tapir.server.tests

import cats.implicits.catsSyntaxEitherId
import org.scalatest.matchers.should.Matchers._
import sttp.client3._
import sttp.model._
import sttp.model.headers.CookieWithMeta
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.tests.Test

class ServerCookieHeadersTests[F[_], OPTIONS, ROUTE](createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE])(implicit m: MonadError[F]) {

  import createServerTest._

  def tests(): List[Test] = requestTests

  val requestTests = List(
    testServerLogic(
      endpoint.in("cookies-test").get.out(setCookies).serverLogic { _ =>
        pureResult(
          List(
            CookieWithMeta.unsafeApply("name1", "value1", path = Some("/path1")),
            CookieWithMeta.unsafeApply("name2", "value2", path = Some("/path2"))
          ).asRight[Unit]
        )
      },
      "Multple Set-Cookie headers should not be compacted"
    ) { (backend, baseUri) =>
      basicRequest.response(asStringAlways).get(uri"$baseUri/cookies-test").send(backend).map { r =>
        r.headers should contain allOf(
          Header.setCookie(CookieWithMeta.unsafeApply("name1", "value1", path = Some("/path1"))),
          Header.setCookie(CookieWithMeta.unsafeApply("name2", "value2", path = Some("/path2")))
        )
      }
    }
  )
}
