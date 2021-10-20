package sttp.tapir.server.interpreter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.Uri._
import sttp.model._
import sttp.monad.MonadError
import sttp.tapir.TestUtil._
import sttp.tapir._
import sttp.tapir.internal.NoStreams
import sttp.tapir.model.{AttributeKey, ConnectionInfo, ServerRequest, ServerResponse}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor._

import scala.collection.immutable

class ServerInterpreterTest extends AnyFlatSpec with Matchers {
  it should "call the interceptors in the correct order" in {
    var callTrail: List[String] = Nil

    class AddToTrailInterceptor(prefix: String) extends EndpointInterceptor[Id] {
      override def apply[B](responder: Responder[Id, B], endpointHandler: EndpointHandler[Id, B]): EndpointHandler[Id, B] =
        new EndpointHandler[Id, B] {
          override def beforeDecode(request: ServerRequest, serverEndpoint: ServerEndpoint[_, _, _, _, Id])(implicit
              monad: MonadError[Id]
          ): Id[BeforeDecodeResult[Id, B]] = {
            callTrail ::= s"$prefix before"
            endpointHandler.beforeDecode(request, serverEndpoint)(idMonadError)
          }

          override def onDecodeSuccess[I](
              ctx: DecodeSuccessContext[Id, I]
          )(implicit monad: MonadError[Id], bodyListener: BodyListener[Id, B]): Id[ServerResponse[B]] = {
            callTrail ::= s"$prefix success"
            endpointHandler.onDecodeSuccess(ctx)(idMonadError, bodyListener)
          }

          override def onDecodeFailure(
              ctx: DecodeFailureContext
          )(implicit monad: MonadError[Id], bodyListener: BodyListener[Id, B]): Id[Option[ServerResponse[B]]] = {
            callTrail ::= s"$prefix failure"
            endpointHandler.onDecodeFailure(ctx)(idMonadError, bodyListener)
          }
        }
    }

    val testRequest = new ServerRequest {
      override def protocol: String = ""
      override def connectionInfo: ConnectionInfo = ConnectionInfo(None, None, None)
      override def underlying: Any = ()
      override def pathSegments: List[String] = Nil
      override def queryParameters: QueryParams = QueryParams.fromSeq(List(("x", "1")))
      override def method: Method = Method.GET
      override def uri: Uri = uri"http://example.com"
      override def headers: immutable.Seq[Header] = Nil
      override def attribute[T](key: AttributeKey[T]): Option[T] = None
      override def withAttribute[T](key: AttributeKey[T], value: T): ServerRequest = this
    }

    // given
    val interceptor1 = new AddToTrailInterceptor("1")
    val interceptor2 = new RequestInterceptor[Id] {
      override def apply[B](
          responder: Responder[Id, B],
          requestHandler: EndpointInterceptor[Id] => RequestHandler[Id, B]
      ): RequestHandler[Id, B] = RequestHandler.from { (request, monad) =>
        callTrail ::= "2 request"
        requestHandler(new AddToTrailInterceptor("2")).apply(request)(monad)
      }
    }
    val interceptor3 = new AddToTrailInterceptor("3")

    val interpreter =
      new ServerInterpreter[Any, Id, Unit, NoStreams](
        TestRequestBody,
        UnitToResponseBody,
        List(interceptor1, interceptor2, interceptor3),
        _ => ()
      )

    // when
    interpreter.apply(testRequest, endpoint.in(query[String]("x")).serverLogic[Id](_ => Right(())))

    // then
    callTrail.reverse shouldBe List("2 request", "1 before", "2 before", "3 before", "1 success", "2 success", "3 success")
  }
}
