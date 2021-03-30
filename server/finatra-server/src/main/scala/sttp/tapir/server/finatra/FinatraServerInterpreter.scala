package sttp.tapir.server.finatra

import com.twitter.finagle.http._
import com.twitter.util.Future
import com.twitter.util.logging.Logging
import sttp.model.Header
import sttp.monad.MonadError
import sttp.tapir.EndpointInput.{FixedMethod, PathCapture}
import sttp.tapir.internal._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interpreter.ServerInterpreter
import sttp.tapir.{Endpoint, EndpointInput}

import scala.reflect.ClassTag

trait FinatraServerInterpreter extends Logging {
  def toRoute[I, E, O](e: Endpoint[I, E, O, Any])(logic: I => Future[Either[E, O]])(implicit
      serverOptions: FinatraServerOptions
  ): FinatraRoute =
    toRoute(e.serverLogic(logic))

  def toRouteRecoverErrors[I, E, O](e: Endpoint[I, E, O, Any])(logic: I => Future[O])(implicit
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E]
  ): FinatraRoute =
    toRoute(e.serverLogicRecoverErrors(logic))

  def toRoute[I, E, O](se: ServerEndpoint[I, E, O, Any, Future])(implicit serverOptions: FinatraServerOptions): FinatraRoute = {
    val handler = { request: Request =>
      val serverRequest = new FinatraServerRequest(request)
      val serverInterpreter = new ServerInterpreter[Any, Future, FinatraContent, Nothing](
        new FinatraRequestBody(request, serverOptions),
        new FinatraToResponseBody,
        serverOptions.interceptors
      )(FutureMonadError)

      serverInterpreter(serverRequest, se).map {
        case None => Response(Status.NotFound)
        case Some(response) =>
          val status = Status(response.code.code)
          val responseWithContent = response.body match {
            case Some(fContent) =>
              val response = fContent match {
                case FinatraContentBuf(buf) =>
                  val r = Response(Version.Http11, status)
                  r.content = buf
                  r
                case FinatraContentReader(reader) => Response(Version.Http11, status, reader)
              }
              response
            case None =>
              Response(Version.Http11, status)
          }

          response.headers.foreach { case Header(name, value) => responseWithContent.headerMap.add(name, value) }

          // If there's a content-type header in headers, override the content-type.
          response.contentType.foreach(ct => responseWithContent.contentType = ct)

          responseWithContent
      }
    }

    FinatraRoute(handler, httpMethod(se.endpoint), path(se.input))
  }

  private[finatra] def path(input: EndpointInput[_]): String = {
    val p = input
      .asVectorOfBasicInputs()
      .collect {
        case segment: EndpointInput.FixedPath[_] => segment.show
        case PathCapture(Some(name), _, _)       => s"/:$name"
        case PathCapture(_, _, _)                => "/:param"
        case EndpointInput.PathsCapture(_, _)    => "/:*"
      }
      .mkString
    if (p.isEmpty) "/:*" else p
  }

  private[finatra] def httpMethod(endpoint: Endpoint[_, _, _, _]): Method = {
    endpoint.input
      .asVectorOfBasicInputs()
      .collectFirst { case FixedMethod(m, _, _) =>
        Method(m.method)
      }
      .getOrElse(Method("ANY"))
  }

  private[finatra] implicit object FutureMonadError extends MonadError[Future] {
    override def unit[T](t: T): Future[T] = Future(t)
    override def map[T, T2](fa: Future[T])(f: (T) => T2): Future[T2] = fa.map(f)
    override def flatMap[T, T2](fa: Future[T])(f: (T) => Future[T2]): Future[T2] = fa.flatMap(f)
    override def error[T](t: Throwable): Future[T] = Future.exception(t)
    override protected def handleWrappedError[T](rt: Future[T])(h: PartialFunction[Throwable, Future[T]]): Future[T] = rt.rescue(h)
    override def ensure[T](f: Future[T], e: => Future[Unit]): Future[T] = f.ensure(e.toJavaFuture.get())
  }
}

object FinatraServerInterpreter extends FinatraServerInterpreter
