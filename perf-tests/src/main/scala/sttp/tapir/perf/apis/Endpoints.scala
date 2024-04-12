package sttp.tapir.perf.apis

import cats.effect.IO
import sttp.tapir._
import sttp.tapir.perf.Common._
import sttp.tapir.server.netty.loom.Id
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.model.EndpointExtensions._

import java.io.File
import scala.concurrent.Future

trait Endpoints {
  type EndpointGen = Int => PublicEndpoint[_, String, String, Any]
  type ServerEndpointGen[F[_]] = Int => ServerEndpoint[Any, F]

  def serverEndpoints[F[_]](reply: String => F[String]): List[ServerEndpointGen[F]] = {
    List(
      { (n: Int) =>
        endpoint.get
          .in("path" + n.toString)
          .in(path[Int]("id"))
          .out(stringBody)
          .serverLogicSuccess { id =>
            reply((id + n).toString)
          }
      },
      { (n: Int) =>
        endpoint.post
          .in("path" + n.toString)
          .in(stringBody)
          .maxRequestBodyLength(LargeInputSize + 1024L)
          .out(stringBody)
          .serverLogicSuccess {
            body: String =>
              reply(s"Ok [$n], string length = ${body.length}")
          }
      },
      { (n: Int) =>
        endpoint.post
          .in("pathBytes" + n.toString)
          .in(byteArrayBody)
          .maxRequestBodyLength(LargeInputSize + 1024L)
          .out(stringBody)
          .serverLogicSuccess { body: Array[Byte] =>
            var x = math.Pi
            for (_ <- 0 until 10000000) {
              x = math.sqrt(x) + 0.3
            }
            reply(s"Ok [$n], bytes length = ${body.length}")
          }
      },
      { (n: Int) =>
        endpoint.post
          .in("pathFile" + n.toString)
          .in(fileBody)
          .maxRequestBodyLength(LargeInputSize + 1024L)
          .out(stringBody)
          .serverLogicSuccess {
            body: File =>
              reply(s"Ok [$n], file saved to ${body.toPath}")
          }
      }
    )
  }

  val wsBaseEndpoint = endpoint.get.in("ws" / "ts")

  def genServerEndpoints[F[_]](routeCount: Int)(reply: String => F[String]): List[ServerEndpoint[Any, F]] =
    serverEndpoints[F](reply).flatMap(gen => (0 to routeCount).map(i => gen(i)))

  def genEndpointsFuture(count: Int): List[ServerEndpoint[Any, Future]] = genServerEndpoints(count)(Future.successful)
  def genEndpointsIO(count: Int): List[ServerEndpoint[Any, IO]] = genServerEndpoints(count)(IO.pure)
  def genEndpointsId(count: Int): List[ServerEndpoint[Any, Id]] = genServerEndpoints[Id](count)(x => x: Id[String])
}
