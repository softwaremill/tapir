package sttp.tapir.server.stub

import cats.effect.IO
import io.circe.{Decoder, Encoder}
import org.scalatest.{FlatSpec, Matchers}
import sttp.tapir._
import sttp.tapir.json.circe._
import cats.syntax.either._
import io.circe.generic.semiauto._
import sttp.client.impl.cats.implicits._
import sttp.client._
import sttp.tapir.client.sttp._
import sttp.tapir.server.ServerEndpoint
class SttpStubServerTest extends FlatSpec with Matchers {

  behavior of "SttpStubServer"

  it should "stub a simple endpoint with custom logic" in {
    // given
    val endpoint = sttp.tapir.endpoint
      .in("api" / "sometest")
      .get
      .out(jsonBody[ResponseWrapper])

    val endpointWithStubLogic: ServerEndpoint[Unit, Unit, ResponseWrapper, Nothing, IO] = endpoint.serverLogic { _ =>
      IO(().asLeft[ResponseWrapper])
    //IO(ResponseWrapper(44.414).asRight[Unit])
    //    IO.raiseError[Either[Unit, ResponseWrapper]](new Exception("Whhopise"))
    }

    implicit val backend = List(endpointWithStubLogic).toBackendStub
    val req = endpoint.toSttpRequestUnsafe(uri"http://test.com").apply(())

    // when
    val x = req.send().unsafeRunSync()

    // then
    println(x)
  }
}

final case class ResponseWrapper(response: Double)

object ResponseWrapper {
  implicit val encoder: Encoder[ResponseWrapper] = deriveEncoder[ResponseWrapper]
  implicit val decoder: Decoder[ResponseWrapper] = deriveDecoder[ResponseWrapper]
}
