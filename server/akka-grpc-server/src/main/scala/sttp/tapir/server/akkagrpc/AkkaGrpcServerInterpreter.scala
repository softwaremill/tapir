package sttp.tapir.server.akkagrpc

import akka.stream.Materializer
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaResponseBody}
import sttp.tapir.server.interpreter.{RequestBody, ToResponseBody}

import scala.concurrent.{ExecutionContext, Future}

trait AkkaGrpcServerInterpreter extends AkkaHttpServerInterpreter {
  override protected def requestBody: (Materializer, ExecutionContext) => RequestBody[Future, AkkaStreams] =
    new AkkaGrpcRequestBody(akkaHttpServerOptions)(_, _)

  override protected def toResponseBody: (Materializer, ExecutionContext) => ToResponseBody[AkkaResponseBody, AkkaStreams] =
    new AkkaGrpcToResponseBody()(_, _)
}
