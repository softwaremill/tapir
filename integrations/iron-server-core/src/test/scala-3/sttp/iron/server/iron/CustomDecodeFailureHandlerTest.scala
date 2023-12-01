package sttp.iron.server.iron

import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.constraint.string.Match
import io.github.iltotore.iron.:|
import io.circe.generic.auto.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.*
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.{Schema, Validator}
import sttp.tapir.generic.auto.*
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.codec.iron.given
import io.github.iltotore.iron.{Constraint, refineEither}
import org.scalatest.Inside.inside
import sttp.capabilities.Streams
import sttp.model.*
import sttp.model.Uri.*
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.interpreter.{RawValue, RequestBody, ServerInterpreter}
import sttp.tapir.TestUtil.*
import sttp.tapir.server.TestUtil.{StringToResponseBody, stringBodyListener}
import sttp.tapir.server.interceptor.RequestResult.Response
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureInterceptor
import sttp.tapir.server.model.ServerResponse

class CustomDecodeFailureHandlerTest extends AnyFlatSpec with Matchers {

  "ironFailureHandler" should "create error message with text provided by iron" in {

    type Guard = Match["^[A-Z][a-z]+$"]
    type Name = String :| Guard
    case class Person(name: Name, address: String)

    inline given Encoder[Name] = summon[Encoder[String]].contramap(_.toString)
    inline given (using inline constraint: Constraint[String, Guard]): Decoder[Name] = summon[Decoder[String]].map(unrefinedValue =>
      unrefinedValue.refineEither[Guard] match
        case Right(value)       => value
        case Left(errorMessage) => throw IronException(s"Could not refine value $unrefinedValue: $errorMessage")
    )

    val input = jsonBody[Person]
    val ep = endpoint.get.in("person").in(input).out(sttp.tapir.stringBody).serverLogicSuccess[Id](_ => "ok")

    val interpreter =
      new ServerInterpreter[Any, Id, String, NoStreams](
        _ => List(ep),
        TestRequestBody,
        StringToResponseBody,
        List(ironDecodeFailureInterceptor[Id]),
        _ => ()
      )

    val req = testRequest(List("person"), """ { "name": "1234", "address": "asdf" } """)

    val expectedErrorMsg = "Invalid value for: body (Could not refine value 1234: Should match ^[A-Z][a-z]+$)"
    val result = interpreter.apply(req)
    inside(result) {
      case Response(ServerResponse(StatusCode.BadRequest, _, Some(errorMsg), _)) =>
        errorMsg shouldBe expectedErrorMsg
    }
  }

  object TestRequestBody extends RequestBody[Id, NoStreams] {
    override val streams: Streams[NoStreams] = NoStreams

    override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R]): Id[RawValue[R]] =
      val body = serverRequest.underlying.asInstanceOf[String]
      RawValue(body.asInstanceOf[R])

    override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = ???
  }

  def testRequest(reqPathSegments: List[String], body: String): ServerRequest = new ServerRequest {
    override def protocol: String = "http"
    override def connectionInfo: ConnectionInfo = ???
    override def underlying: Any = body
    override def pathSegments: List[String] = reqPathSegments
    override def queryParameters: QueryParams = QueryParams()
    override def method: Method = Method.GET
    override def uri: Uri = uri"http://example.com${reqPathSegments.mkString("/")}"
    override def headers: Seq[Header] = Nil
    override def attribute[T](k: AttributeKey[T]): Option[T] = None
    override def attribute[T](k: AttributeKey[T], v: T): ServerRequest = this
    override def withUnderlying(underlying: Any): ServerRequest = this
  }
}
