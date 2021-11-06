package sttp.tapir.server.ziohttp

import cats.data.NonEmptyList
import cats.effect.IO
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.SttpBackend
import sttp.model.{StatusCode, Uri}
import sttp.tapir.ztapir._
import zhttp.http._
import zio.ZIO
import org.scalatest.Assertions._
import sttp.tapir.server.tests.Testable
import sttp.capabilities.zio.ZioStreams

object ZioHttpCompositionTest {

  private def testComposition = (backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets], baseUri: Uri) => {
    import sttp.client3._

    basicRequest.get(uri"$baseUri/p1").send(backend) product
      basicRequest.get(uri"$baseUri/p2").send(backend) product
      basicRequest.get(uri"$baseUri/p3").send(backend)map{
      case ((res1, res2), res3) =>
        assert(
          res1.code === StatusCode.Ok &&
          res2.code === StatusCode.Ok &&
          res3.code === StatusCode.BadRequest
        )
    }
  }

  val testable: Testable[RHttpApp[Any]] = {

    val ep1 = endpoint.get.in("p1").zServerLogic(_ => ZIO.unit)
    val ep3 = endpoint.get.in("p3").zServerLogic(_ => ZIO.fail(new RuntimeException("boom")))

    val route1: RHttpApp[Any] = ZioHttpInterpreter().toHttp(ep1)
    val route2: RHttpApp[Any] = Http.collect {
      case Method.GET -> Root / "p2" => Response.ok
    }
    val route3: RHttpApp[Any] = ZioHttpInterpreter().toHttp(ep3)

    Testable(
      "zio http apps compose after creation",
      () => NonEmptyList.of(route3, route1, route2),
      testComposition
      )
    }

}
