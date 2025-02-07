package sttp.tapir.server.mockserver

import io.circe.{JsonObject, Printer}
import io.circe.syntax._
import sttp.client3._
import sttp.client3.testing._
import sttp.model.Uri.{QuerySegment, UriContext}
import sttp.model.{ContentTypeRange, HasHeaders, Header, HeaderNames, Headers, MediaType, StatusCode, Uri}
import sttp.tapir.internal.ParamsAsAny
import sttp.tapir.{CodecFormat, DecodeResult, Endpoint, RawBodyType, WebSocketBodyOutput}
import sttp.tapir.server.mockserver.impl.JsonCodecs._
import cats.syntax.either._

import java.nio.charset.Charset
import io.circe.parser._
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.server.interpreter.{EncodeOutputs, OutputValues, ToResponseBody}

import scala.collection.immutable.Seq

class SttpMockServerClient[F[_]] private[mockserver] (baseUri: Uri, backend: SttpBackend[F, Any]) {

  import SttpMockServerClient._

  private val F = backend.responseMonad

  def whenInputMatches[A, E, I, O](
      endpoint: Endpoint[A, I, E, O, Any]
  )(securityInput: A, input: I): SttpMockServerClient.TypeAwareWhenRequest[F, A, I, E, O] =
    new SttpMockServerClient.TypeAwareWhenRequest[F, A, I, E, O](endpoint, securityInput, input, baseUri)(backend)

  def verifyRequest[A, E, I, O](
      endpoint: Endpoint[A, I, E, O, Any],
      times: VerificationTimes = VerificationTimes.exactlyOnce
  )(securityInput: A, input: I): F[ExpectationMatched] = {

    val verifyExpectationRequest = VerifyExpectationRequest(
      httpRequest = toExpectationRequest(endpoint, securityInput, input),
      times = times.toDefinition
    )

    val responseF = basicRequest
      .put(uri"$baseUri/mockserver/verify")
      .contentType(MediaType.ApplicationJson)
      .body(printer.print(verifyExpectationRequest.asJson))
      .send(backend)

    F.flatMap(responseF) { response =>
      handleResponse(response)(
        onBadRequest = MockServerException.IncorrectRequestFormat,
        onNotAcceptable = MockServerException.InvalidExpectation
      )(onSuccess = _ => Right(ExpectationMatched))
        .fold(F.error, F.unit)
    }
  }

  def clear: F[Unit] = {
    val responseF = basicRequest
      .put(uri"$baseUri/mockserver/clear")
      .send(backend)

    F.flatMap(responseF) {
      handleSimpleResponse(_).fold(F.error, F.unit)
    }
  }

  def reset: F[Unit] = {
    val responseF = basicRequest
      .put(uri"$baseUri/mockserver/reset")
      .send(backend)

    F.flatMap(responseF) {
      handleSimpleResponse(_).fold(F.error, F.unit)
    }
  }
}

object SttpMockServerClient {
  def apply[F[_]](baseUri: Uri, backend: SttpBackend[F, Any]): SttpMockServerClient[F] =
    new SttpMockServerClient[F](baseUri, backend)

  class TypeAwareWhenRequest[F[_], A, I, E, O] private[mockserver] (
      endpoint: Endpoint[A, I, E, O, Any],
      securityInput: A,
      input: I,
      baseUri: Uri
  )(
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

      val outputValues = toOutputValues(endpoint)(expectedOutput)

      val createExpectationRequest: CreateExpectationRequest = CreateExpectationRequest(
        httpRequest = toExpectationRequest(endpoint, securityInput, input),
        httpResponse = toExpectationResponse(outputValues, statusCode)
      )

      val responseF = basicRequest
        .put(uri"$baseUri/mockserver/expectation")
        .contentType(MediaType.ApplicationJson)
        .body(printer.print(createExpectationRequest.asJson))
        .send(backend)

      F.flatMap(responseF) { response =>
        handleResponse(response)(
          onBadRequest = MockServerException.IncorrectRequestFormat,
          onNotAcceptable = MockServerException.InvalidExpectation
        )(onSuccess = body => decode[List[Expectation]](body))
          .fold(F.error, F.unit)
      }
    }

  }

  private def toExpectationRequest[A, E, I, O](
      endpoint: Endpoint[A, I, E, O, Any],
      securityInput: A,
      input: I
  ): ExpectationRequestDefinition = {
    val request = SttpClientInterpreter().toSecureRequest(endpoint, None).apply(securityInput).apply(input)
    ExpectationRequestDefinition(
      method = request.method,
      path = request.uri.copy(querySegments = Seq()),
      queryStringParameters = querySegmentsToMultiMapOpt(request.uri.querySegments),
      body = toExpectationBody(request),
      headers = headersToMultiMapOpt(request.headers)
    )
  }

  private def toExpectationResponse[E, I, O](outputValues: OutputValues[Any], statusCode: StatusCode): ExpectationResponseDefinition = {
    ExpectationResponseDefinition(
      statusCode = outputValues.statusCode.getOrElse(statusCode),
      body = toExpectationBody(outputValues),
      headers = headersToMultiMapOpt(outputValues.headers)
    )
  }

  private def toExpectationBody[E, O](request: Request[DecodeResult[Either[E, O]], Any]): Option[ExpectationBodyDefinition] = {
    for {
      body <- Option(new String(request.forceBodyAsByteArray))
      if body.nonEmpty
      contentTypeRaw <- (request: HasHeaders).contentType
      contentType <- MediaType.parse(contentTypeRaw).toOption
    } yield stringToBodyDefinition(contentType)(body)
  }

  private def toExpectationBody(outputValues: OutputValues[Any]): Option[ExpectationBodyDefinition] = {
    for {
      body <- outputValues.body.map(_.apply(Headers(outputValues.headers)).toString)
      if body.nonEmpty
      contentTypeRaw <- outputValues.headers.find(_.name == HeaderNames.ContentType)
      contentType <- MediaType.parse(contentTypeRaw.value).toOption
    } yield stringToBodyDefinition(contentType)(body)
  }

  private def stringToBodyDefinition(contentType: MediaType)(body: String) = {
    contentType match {
      case MediaType.ApplicationJson =>
        ExpectationBodyDefinition.JsonBodyDefinition(
          json = decode[JsonObject](body).valueOr(throw _), // todo: probably it should not throw if tapir interprets correctly
          matchType = ExpectationBodyDefinition.JsonMatchType.Strict
        )
      case other => ExpectationBodyDefinition.PlainBodyDefinition(body, other)
    }
  }

  private def toOutputValues[A, E, I, O](
      endpoint: Endpoint[A, I, E, O, Any]
  )(expectedOutput: Either[E, O]): OutputValues[Any] = {

    val responseValue = expectedOutput.merge
    val toResponseBody: ToResponseBody[Any, NoStreams] = new ToResponseBody[Any, NoStreams] {
      override val streams: NoStreams = NoStreams
      override def fromRawValue[RAW](v: RAW, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[RAW]): Any = v
      override def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): Any = v
      override def fromWebSocketPipe[REQ, RESP](
          pipe: streams.Pipe[REQ, RESP],
          o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
      ): Any = pipe // impossible
    }
    new EncodeOutputs[Any, NoStreams](toResponseBody, Seq(ContentTypeRange.AnyRange))
      .apply(
        expectedOutput.fold(
          _ => endpoint.errorOutput,
          _ => endpoint.output
        ),
        ParamsAsAny(responseValue),
        OutputValues.empty
      )
  }

  private def headersToMultiMapOpt(headers: Seq[Header]): Option[Map[String, List[String]]] =
    if (headers.isEmpty) None
    else Some(headers.groupBy(_.name).map { case (name, values) => name -> values.map(_.value).toList })

  private def querySegmentsToMultiMapOpt(querySegments: Seq[QuerySegment]): Option[Map[String, List[String]]] =
    if (querySegments.isEmpty) None
    else
      Some(
        querySegments
          .collect { case kv: QuerySegment.KeyValue => kv }
          .groupBy(x => x.k)
          .map { case (key, values) => key -> values.map(_.v).toList }
      )

  private val printer = Printer.noSpaces

  private def handleSimpleResponse(response: Response[Either[String, String]]): Either[Throwable, Unit] = {
    response.body.left.map(error => MockServerException.UnexpectedError(response.code, error)).map(_ => ())
  }

  private def handleResponse[A](response: Response[Either[String, String]])(
      onBadRequest: String => Throwable,
      onNotAcceptable: String => Throwable
  )(onSuccess: String => Either[Throwable, A]): Either[Throwable, A] = {
    response.body.left
      .map { errorBody =>
        if (response.code == StatusCode.BadRequest) onBadRequest(errorBody)
        else if (response.code == StatusCode.NotAcceptable) onNotAcceptable(errorBody)
        else MockServerException.UnexpectedError(response.code, errorBody)
      }
      .flatMap(onSuccess)
  }
}
