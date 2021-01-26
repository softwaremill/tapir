package sttp.tapir.client.play

import play.api.libs.ws.{StandaloneWSClient, StandaloneWSRequest, StandaloneWSResponse}
import sttp.capabilities.Effect
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.{DecodeResult, Endpoint}

import scala.concurrent.{ExecutionContext, Future}

trait PlayClientInterpreter {

  /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
    * uri.
    *
    * Returns:
    * - a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
    * to appropriate request parameters: path, query, headers and body. The result is a `StandaloneWSRequest`,
    * which can be sent using the `execute()` method.
    * - a response parser to use on the `StandaloneWSResponse` obtained after executing the request.
    */
  def toRequest[I, E, O](e: Endpoint[I, E, O, AkkaStreams with Effect[Future]], baseUri: String)(implicit
      clientOptions: PlayClientOptions,
      ws: StandaloneWSClient,
      ec: ExecutionContext
  ): I => Future[(StandaloneWSRequest, StandaloneWSResponse => Future[DecodeResult[Either[E, O]]])] =
    new EndpointToPlayClient(clientOptions, ws).toPlayRequest(e, baseUri)

  /** Interprets the endpoint as a client call, using the given `baseUri` as the starting point to create the target
    * uri.
    *
    * Returns:
    * - a function which, when applied to the endpoint's input parameters (given as a tuple), will encode them
    * to appropriate request parameters: path, query, headers and body. The result is a `StandaloneWSRequest`,
    * which can be sent using the `execute()` method.
    * - a response parser to use on the `StandaloneWSResponse` obtained after executing the request.
    *
    * @throws IllegalArgumentException when response parsing fails
    */
  def toRequestUnsafe[I, E, O](e: Endpoint[I, E, O, AkkaStreams with Effect[Future]], baseUri: String)(implicit
      clientOptions: PlayClientOptions,
      ws: StandaloneWSClient,
      ec: ExecutionContext
  ): I => Future[(StandaloneWSRequest, StandaloneWSResponse => Future[Either[E, O]])] =
    new EndpointToPlayClient(clientOptions, ws).toPlayRequestUnsafe(e, baseUri)

}

object PlayClientInterpreter extends PlayClientInterpreter
