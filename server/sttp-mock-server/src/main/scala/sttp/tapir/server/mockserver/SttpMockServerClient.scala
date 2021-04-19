package sttp.tapir.server.mockserver

import io.circe.Printer
import io.circe.syntax._
import sttp.client3._
import sttp.client3.testing._
import sttp.model.Uri.UriContext
import sttp.model.{ContentTypeRange, HasHeaders, Header, Headers, MediaType, StatusCode, Uri}
import sttp.tapir.internal.{NoStreams, ParamsAsAny}
import sttp.tapir.{CodecFormat, Endpoint, RawBodyType, WebSocketBodyOutput}
import sttp.tapir.server.mockserver.impl.JsonCodecs._
import java.nio.charset.Charset
import io.circe.parser._
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.server.interpreter.{EncodeOutputs, OutputValues, ToResponseBody}
import scala.collection.immutable.Seq

class SttpMockServerClient[F[_]] private[mockserver] (baseUri: Uri, backend: SttpBackend[F, Any]) {

  def whenInputMatches[E, I, O](
      endpoint: Endpoint[I, E, O, Any]
  )(input: I): SttpMockServerClient.TypeAwareWhenRequest[F, I, E, O] =
    new SttpMockServerClient.TypeAwareWhenRequest[F, I, E, O](endpoint, input, baseUri)(backend)

}

object SttpMockServerClient {
  def apply[F[_]](baseUri: Uri, backend: SttpBackend[F, Any]): SttpMockServerClient[F] =
    new SttpMockServerClient[F](baseUri, backend)

  class TypeAwareWhenRequest[F[_], I, E, O] private[mockserver] (endpoint: Endpoint[I, E, O, Any], input: I, baseUri: Uri)(
      backend: SttpBackend[F, Any]
  ) {

    private val F = backend.responseMonad

    def thenSuccess(response: O): F[List[Expectation]] =
      thenRespondWithOutput(Right(response), StatusCode.Ok)

    def thenError(errorResponse: E, statusCode: StatusCode): F[List[Expectation]] =
      thenRespondWithOutput(Left(errorResponse), statusCode)

    private def thenRespondWithOutput(
        expectedOutput: Either[E, O],
        statusCode: StatusCode
    ): F[List[Expectation]] = {
      val request = SttpClientInterpreter.toRequest(endpoint, None).apply(input)

      val requestBody = Option(new String(request.forceBodyAsByteArray)).filter(_.nonEmpty)

      val response = {
        val responseValue = expectedOutput.merge
        val toResponseBody: ToResponseBody[Any, Nothing] = new ToResponseBody[Any, Nothing] {
          override val streams: NoStreams = NoStreams
          override def fromRawValue[RAW](v: RAW, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[RAW]): Any = v
          override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): Any = v
          override def fromWebSocketPipe[REQ, RESP](
              pipe: streams.Pipe[REQ, RESP],
              o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Nothing]
          ): Any = pipe // impossible
        }
        new EncodeOutputs[Any, Nothing](toResponseBody, Seq(ContentTypeRange.AnyRange))
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
          headers = headersToMultiMapOpt(request.headers)
        ),
        httpResponse = ExpectationResponseDefinition(
          body = response.body.map(_.apply(Headers(response.headers)).toString),
          headers = headersToMultiMapOpt(response.headers),
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

    private def headersToMultiMapOpt(headers: Seq[Header]): Option[Map[String, List[String]]] =
      if (headers.isEmpty) None
      else Some(headers.groupBy(_.name).map { case (name, values) => name -> values.map(_.value).toList })

    private val printer = Printer.noSpaces.copy(dropNullValues = true)

  }
}
