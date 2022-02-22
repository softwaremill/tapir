package sttp.tapir.server.interpreter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.Uri._
import sttp.model._
import sttp.monad.MonadError
import sttp.tapir.TestUtil._
import sttp.tapir._
import sttp.tapir.internal.NoStreams
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.server.interceptor._

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
  }

  it should "call the interceptors in the correct order" in {
    val callTrail: collection.mutable.ListBuffer[String] = collection.mutable.ListBuffer.empty

    // given
    val interceptor1 = new AddToTrailInterceptor(callTrail.append(_: String), "1")
    // should be called first, as it's the only request interceptor; creates an endpoint interceptor, which should be
    // added to the endpoint interceptor stack in the correct place
    val interceptor2 = new RequestInterceptor[Id] {
      override def apply[B](
          responder: Responder[Id, B],
          requestHandler: EndpointInterceptor[Id] => RequestHandler[Id, B]
      ): RequestHandler[Id, B] = RequestHandler.from { (request, monad) =>
        callTrail.append("2 request")
        requestHandler(new AddToTrailInterceptor(callTrail.append(_: String), "2")).apply(request)(monad)
      }
    }
    val interceptor3 = new AddToTrailInterceptor(callTrail.append(_: String), "3")

    val interpreter =
      new ServerInterpreter[Any, Id, Unit, NoStreams](
        List(endpoint.in(query[String]("x")).serverLogic[Id](_ => Right(()))),
        UnitToResponseBody,
        List(interceptor1, interceptor2, interceptor3),
        _ => ()
      )

    // when
    interpreter.apply(testRequest, TestRequestBody)

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
      new ServerInterpreter[Any, Id, Unit, NoStreams](
        List(
          endpoint
            .securityIn(query[StringWrapper]("x")(Codec.listHead(addToTrailCodec("x"))))
            .in(query[StringWrapper]("y")(Codec.listHead(addToTrailCodec("y"))))
            .in(plainBody[StringWrapper](addToTrailCodec("z")))
            .serverSecurityLogic[Unit, Id](_ => Left(()))
            .serverLogic(_ => _ => Right(()))
        ),
        UnitToResponseBody,
        List(new AddToTrailInterceptor(callTrail.append(_: String), "1")),
        _ => ()
      )

    // when
    interpreter.apply(testRequest, TestRequestBody)

    // then
    callTrail.toList shouldBe List("x decode", "y decode", "1 security failure")
  }

  class AddToTrailInterceptor(addCallTrail: String => Unit, prefix: String) extends EndpointInterceptor[Id] {
    override def apply[B](responder: Responder[Id, B], endpointHandler: EndpointHandler[Id, B]): EndpointHandler[Id, B] =
      new EndpointHandler[Id, B] {
        override def onDecodeSuccess[U, I](
            ctx: DecodeSuccessContext[Id, U, I]
        )(implicit monad: MonadError[Id], bodyListener: BodyListener[Id, B]): Id[ServerResponseFromOutput[B]] = {
          addCallTrail(s"$prefix success")
          endpointHandler.onDecodeSuccess(ctx)(idMonadError, bodyListener)
        }

        override def onSecurityFailure[A](ctx: SecurityFailureContext[Id, A])(implicit
            monad: MonadError[Id],
            bodyListener: BodyListener[Id, B]
        ): Id[ServerResponseFromOutput[B]] = {
          addCallTrail(s"$prefix security failure")
          endpointHandler.onSecurityFailure(ctx)(idMonadError, bodyListener)
        }

        override def onDecodeFailure(
            ctx: DecodeFailureContext
        )(implicit monad: MonadError[Id], bodyListener: BodyListener[Id, B]): Id[Option[ServerResponseFromOutput[B]]] = {
          addCallTrail(s"$prefix failure")
          endpointHandler.onDecodeFailure(ctx)(idMonadError, bodyListener)
        }
      }
  }
}
