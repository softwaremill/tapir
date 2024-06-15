package sttp.tapir.server.interpreter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.Uri._
import sttp.model._
import sttp.monad.MonadError
import sttp.shared.Identity
import sttp.tapir.TestUtil._
import sttp.tapir._
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.server.TestUtil._
import sttp.tapir.server.interceptor.RequestResult.Response
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interceptor.reject.{DefaultRejectHandler, RejectInterceptor}
import sttp.tapir.server.model.{ServerResponse, ValuedEndpointOutput}

import scala.collection.immutable

class ServerInterpreterTest extends AnyFlatSpec with Matchers {
  val testRequest: ServerRequest = new ServerRequest {
    override def protocol: String = ""
    override def connectionInfo: ConnectionInfo = ConnectionInfo(None, None, None)
    override def underlying: Any = ()
    override def pathSegments: List[String] = Nil
    override def queryParameters: QueryParams = QueryParams.fromSeq(List(("x", "1"), ("y", "2")))
    override def method: Method = Method.GET
    override def uri: Uri = uri"http://example.com"
    override def headers: immutable.Seq[Header] = Nil
    override def attribute[T](k: AttributeKey[T]): Option[T] = None
    override def attribute[T](k: AttributeKey[T], v: T): ServerRequest = this
    override def withUnderlying(underlying: Any): ServerRequest = this
  }

  it should "call the interceptors in the correct order" in {
    val callTrail: collection.mutable.ListBuffer[String] = collection.mutable.ListBuffer.empty

    // given
    val interceptor1 = new AddToTrailInterceptor(callTrail.append(_: String), "1")
    // should be called first, as it's the only request interceptor; creates an endpoint interceptor, which should be
    // added to the endpoint interceptor stack in the correct place
    val interceptor2 = new RequestInterceptor[Identity] {
      override def apply[R, B](
          responder: Responder[Identity, B],
          requestHandler: EndpointInterceptor[Identity] => RequestHandler[Identity, R, B]
      ): RequestHandler[Identity, R, B] = RequestHandler.from { (request, endpoints, monad) =>
        callTrail.append("2 request")
        requestHandler(new AddToTrailInterceptor(callTrail.append(_: String), "2")).apply(request, endpoints)(monad)
      }
    }
    val interceptor3 = new AddToTrailInterceptor(callTrail.append(_: String), "3")

    val interpreter =
      new ServerInterpreter[Any, Identity, Unit, NoStreams](
        _ => List(endpoint.in(query[String]("x")).handle(_ => Right(()))),
        TestRequestBody,
        UnitToResponseBody,
        List(interceptor1, interceptor2, interceptor3),
        _ => ()
      )

    // when
    interpreter.apply(testRequest)

    // then
    callTrail.toList shouldBe List("2 request", "1 success", "2 success", "3 success")
  }

  it should "decode security inputs, basic regular inputs, and not decode the body, when security logic fails" in {
    val callTrail: collection.mutable.ListBuffer[String] = collection.mutable.ListBuffer.empty

    // given
    case class StringWrapper(s: String)
    def addToTrailCodec(prefix: String) = Codec.string.mapDecode { s =>
      callTrail.append(s"$prefix decode")
      DecodeResult.Value(StringWrapper(s))
    }(_.s)

    val interpreter =
      new ServerInterpreter[Any, Identity, Unit, NoStreams](
        _ =>
          List(
            endpoint
              .securityIn(query[StringWrapper]("x")(Codec.listHead(addToTrailCodec("x"))))
              .in(query[StringWrapper]("y")(Codec.listHead(addToTrailCodec("y"))))
              .in(plainBody[StringWrapper](addToTrailCodec("z")))
              .serverSecurityLogic[Unit, Identity](_ => Left(()))
              .serverLogic(_ => _ => Right(()))
          ),
        TestRequestBody,
        UnitToResponseBody,
        List(new AddToTrailInterceptor(callTrail.append(_: String), "1")),
        _ => ()
      )

    // when
    interpreter.apply(testRequest)

    // then
    callTrail.toList shouldBe List("x decode", "y decode", "1 security failure")
  }

  it should "use a customized reject interceptor" in {
    // given
    val customStatusCode = StatusCode.BadRequest
    val customBody = "Custom body"

    val rejectInterceptor = new RejectInterceptor[Identity](
      DefaultRejectHandler((_, _) => ValuedEndpointOutput(statusCode.and(stringBody), (customStatusCode, customBody)), None)
    )

    val interpreter =
      new ServerInterpreter[Any, Identity, String, NoStreams](
        _ =>
          List(
            endpoint.post.serverLogic[Identity](_ => Right(())),
            endpoint.put.serverLogic[Identity](_ => Right(()))
          ),
        TestRequestBody,
        StringToResponseBody,
        List(rejectInterceptor),
        _ => ()
      )

    // when
    val response = interpreter(testRequest)

    // then
    response should matchPattern { case Response(ServerResponse(_, _, Some(_), _)) => }
  }

  class AddToTrailInterceptor(addCallTrail: String => Unit, prefix: String) extends EndpointInterceptor[Identity] {
    override def apply[B](responder: Responder[Identity, B], endpointHandler: EndpointHandler[Identity, B]): EndpointHandler[Identity, B] =
      new EndpointHandler[Identity, B] {
        override def onDecodeSuccess[A, U, I](
            ctx: DecodeSuccessContext[Identity, A, U, I]
        )(implicit monad: MonadError[Identity], bodyListener: BodyListener[Identity, B]): Identity[ServerResponse[B]] = {
          addCallTrail(s"$prefix success")
          endpointHandler.onDecodeSuccess(ctx)(idMonad, bodyListener)
        }

        override def onSecurityFailure[A](ctx: SecurityFailureContext[Identity, A])(implicit
            monad: MonadError[Identity],
            bodyListener: BodyListener[Identity, B]
        ): Identity[ServerResponse[B]] = {
          addCallTrail(s"$prefix security failure")
          endpointHandler.onSecurityFailure(ctx)(idMonad, bodyListener)
        }

        override def onDecodeFailure(
            ctx: DecodeFailureContext
        )(implicit monad: MonadError[Identity], bodyListener: BodyListener[Identity, B]): Identity[Option[ServerResponse[B]]] = {
          addCallTrail(s"$prefix failure")
          endpointHandler.onDecodeFailure(ctx)(idMonad, bodyListener)
        }
      }
  }
}
