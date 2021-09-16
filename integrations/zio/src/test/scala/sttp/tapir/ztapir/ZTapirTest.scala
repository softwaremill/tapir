package sttp.tapir.ztapir

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.{BodyListener, RawValue, RequestBody, ServerInterpreter, ToResponseBody}
import sttp.capabilities.{Streams, WebSockets}
import sttp.model.{HasHeaders, Header, Method, QueryParams, StatusCode, Uri}
import sttp.tapir.{CodecFormat, Endpoint, RawBodyType, WebSocketBodyOutput}
import sttp.tapir.model.{ConnectionInfo, ServerRequest, ServerResponse}
import zio.{UIO, ZIO}
import sttp.tapir.ztapir.instances.TestMonadError._
import zio.test.DefaultRunnableSpec
import zio.test._
import zio.test.Assertion._
import zio.test.environment._

import java.nio.charset.Charset
import scala.util.{Success, Try}

object ZTapirTest extends DefaultRunnableSpec with ZTapir {

  def spec: ZSpec[TestEnvironment, Any] =
    suite("ZTapir tests")(testZServerLogicErrorHandling, testZServerLogicPartErrorHandling, testZServerLogicForCurrentErrorHandling)

  type ResponseBodyType = String

  type RequestBodyType = String

  private val exampleRequestBody = new RequestBody[TestEffect, RequestBodyType] {
    override val streams: Streams[RequestBodyType] = null.asInstanceOf[Streams[RequestBodyType]]
    override def toRaw[R](bodyType: RawBodyType[R]): TestEffect[RawValue[R]] = ???
    override def toStream(): streams.BinaryStream = ???
  }

  private val exampleToResponse: ToResponseBody[ResponseBodyType, RequestBodyType] = new ToResponseBody[ResponseBodyType, RequestBodyType] {
    override val streams: Streams[RequestBodyType] = null.asInstanceOf[Streams[RequestBodyType]]
    override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): ResponseBodyType = "Sample body"
    override def fromStreamValue(
        v: streams.BinaryStream,
        headers: HasHeaders,
        format: CodecFormat,
        charset: Option[Charset]
    ): ResponseBodyType = ???
    override def fromWebSocketPipe[REQ, RESP](
        pipe: streams.Pipe[REQ, RESP],
        o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, RequestBodyType]
    ): ResponseBodyType = ???
  }

  private val testRequest: ServerRequest = new ServerRequest {
    override def protocol: String = ???
    override def connectionInfo: ConnectionInfo = ???
    override def underlying: Any = ???
    override def pathSegments: List[String] = List("foo", "bar")
    override def queryParameters: QueryParams = QueryParams()
    override def method: Method = ???
    override def uri: Uri = ???
    override def headers: scala.collection.immutable.Seq[Header] = scala.collection.immutable.Seq(Header("X-User-Name", "John"))
  }

  implicit val bodyListener: BodyListener[TestEffect, ResponseBodyType] = new BodyListener[TestEffect, ResponseBodyType] {
    override def onComplete(body: ResponseBodyType)(cb: Try[Unit] => TestEffect[Unit]): TestEffect[String] = {
      cb(Success(())).map(_ => body)
    }
  }

  private def errorToResponse(error: Throwable): UIO[RequestResult.Response[ResponseBodyType]] =
    UIO(
      RequestResult.Response(
        ServerResponse(StatusCode.InternalServerError, scala.collection.immutable.Seq.empty[Header], Some(error.getMessage))
      )
    )

  final case class User(name: String)

  private def failedAutLogic(userName: String): UIO[User] = ZIO(10 / 0).orDie.as(User(userName))

  val interpreter = new ServerInterpreter[ZioStreams with WebSockets, TestEffect, ResponseBodyType, RequestBodyType](
    exampleRequestBody,
    exampleToResponse,
    List.empty,
    _ => ZIO.unit
  )

  private val testZServerLogicErrorHandling = testM("zServerLogic error handling") {
    val testEndpoint: Endpoint[Unit, TestError, String, Any] = endpoint.in("foo" / "bar").errorOut(plainBody[TestError]).out(stringBody)

    def logic(input: Unit): ZIO[Any, TestError, String] = ZIO(10 / 0).orDie.map(_.toString)
    val serverEndpoint: ZServerEndpoint[Any, Unit, TestError, String] = testEndpoint.zServerLogic(logic)

    interpreter[Unit, TestError, String](testRequest, serverEndpoint)
      .catchAll(errorToResponse)
      .map { result =>
        assert(result)(
          isSubtype[RequestResult.Response[String]](hasField("code", _.response.code, equalTo(StatusCode.InternalServerError)))
        )
      }
  }

  private val testZServerLogicPartErrorHandling = testM("zServerLogicPart error handling") {
    val testEndpoint: Endpoint[String, TestError, String, Any] =
      endpoint.in(header[String]("X-User-Name")).in("foo" / "bar").errorOut(plainBody[TestError]).out(stringBody)

    def logic(user: User, rest: Unit): ZIO[Any, TestError, String] = ZIO.succeed("Hello World")

    val serverEndpoint: ZServerEndpoint[Any, String, TestError, String] =
      testEndpoint.zServerLogicPart(failedAutLogic).andThen { case (user, rest) => logic(user, rest) }

    interpreter[String, TestError, String](testRequest, serverEndpoint)
      .catchAll(errorToResponse)
      .map { result =>
        assert(result)(
          isSubtype[RequestResult.Response[String]](hasField("code", _.response.code, equalTo(StatusCode.InternalServerError)))
        )
      }
  }

  private val testZServerLogicForCurrentErrorHandling = testM("zServerLogicForCurrent error handling") {
    val securedEndpoint: ZPartialServerEndpoint[Any, User, Unit, TestError, Unit] =
      endpoint.in(header[String]("X-User-Name")).errorOut(plainBody[TestError]).zServerLogicForCurrent[Any, User](failedAutLogic)

    val testPartialEndpoint: ZPartialServerEndpoint[Any, User, Unit, TestError, String] = securedEndpoint.in("foo" / "bar").out(stringBody)

    def logic(user: User, rest: Unit): ZIO[Any, TestError, String] = ZIO.succeed("Hello World")

    val serverEndpoint: ZServerEndpoint[Any, (testPartialEndpoint.T, Unit), TestError, String] =
      testPartialEndpoint.serverLogic[Any]((logic _).tupled)

    interpreter(testRequest, serverEndpoint)
      .catchAll(errorToResponse)
      .map { result =>
        assert(result)(
          isSubtype[RequestResult.Response[String]](hasField("code", _.response.code, equalTo(StatusCode.InternalServerError)))
        )
      }
  }
}
