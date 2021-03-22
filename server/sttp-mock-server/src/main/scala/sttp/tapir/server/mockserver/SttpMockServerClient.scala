package sttp.tapir.server.mockserver

import io.circe.Printer
import io.circe.syntax._
import sttp.client3._
import sttp.client3.testing._
import sttp.model.Uri.UriContext
import sttp.model.{MediaType, StatusCode, Uri}
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.internal.{NoStreams, ParamsAsAny}
import sttp.tapir.server.internal.{EncodeOutputBody, EncodeOutputs, OutputValues}
import sttp.tapir.{CodecFormat, Endpoint, RawBodyType, WebSocketBodyOutput}
import sttp.tapir.server.mockserver.impl.JsonCodecs._
import java.nio.charset.Charset
import io.circe.parser._

class SttpMockServerClient[F[_]](baseUri: Uri)(backend: SttpBackend[F, Any]) {
  private val F = backend.responseMonad

  def createExpectation[E, I, O](
      endpoint: Endpoint[I, E, O, Any]
  )(input: I, expectedOutput: Either[E, O], statusCode: StatusCode): F[List[Expectation]] = {
    val request = SttpClientInterpreter.toRequest(endpoint, None).apply(input)

    val requestBody = Option(new String(request.forceBodyAsByteArray)).filter(_.nonEmpty)

    val response = {
      val responseValue = expectedOutput.merge
      val encodeOutputBody: EncodeOutputBody[Any, Any, Nothing] = new EncodeOutputBody[Any, Any, Nothing] {
        override val streams: NoStreams.type = NoStreams
        override def rawValueToBody[T](v: T, format: CodecFormat, bodyType: RawBodyType[T]): Any = v
        override def streamValueToBody(v: Nothing, format: CodecFormat, charset: Option[Charset]): Any = v
        override def webSocketPipeToBody[REQ, RESP](
            pipe: streams.Pipe[REQ, RESP],
            o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Nothing]
        ): Any = pipe //impossible
      }
      new EncodeOutputs[Any, Any, Nothing](encodeOutputBody)
        .apply(
          expectedOutput.fold(
            _ => endpoint.errorOutput,
            _ => endpoint.output
          ),
          ParamsAsAny(responseValue),
          OutputValues.empty
        )
    }

    val createExpectationRequest: CreateExpectationRequest = CreateExpectationRequest(
      httpRequest = ExpectationRequestDefinition(
        method = request.method,
        path = request.uri,
        body = requestBody,
        headers = if (request.headers.isEmpty) None else Some(request.headers.groupBy(_.name).mapValues(_.toList.map(_.value)).toMap)
      ),
      httpResponse = ExpectationResponseDefinition(
        body = response.body.map(_.merge.toString),
        headers = if (response.headers.isEmpty) None else Some(response.headers.groupBy(_._1).mapValues(_.map(_._2).toList).toMap),
        statusCode = response.statusCode.getOrElse(statusCode)
      )
    )

    val responseF = basicRequest
      .put(uri"$baseUri/mockserver/expectation")
      .contentType(MediaType.ApplicationJson)
      .body(printer.print(createExpectationRequest.asJson))
      .send(backend)

    F.flatMap(responseF) { response =>
      response.body.fold(
        errorBody => F.error(new RuntimeException(errorBody)),
        body => decode[List[Expectation]](body).fold(F.error, F.unit)
      )
    }
  }

  private val printer = Printer.noSpaces.copy(dropNullValues = true)
}
