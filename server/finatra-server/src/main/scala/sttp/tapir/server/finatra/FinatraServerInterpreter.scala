package sttp.tapir.server.finatra

import com.twitter.finagle.http._
import com.twitter.util.Future
import com.twitter.util.logging.Logging
import sttp.monad.MonadError
import sttp.tapir.EndpointInput.PathCapture
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.internal._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.finatra.FinatraServerInterpreter.FutureMonadError
import sttp.tapir.server.interceptor.RequestResult
import sttp.tapir.server.interpreter.ServerInterpreter
import sttp.tapir._

trait FinatraServerInterpreter extends Logging {

  def finatraServerOptions: FinatraServerOptions = FinatraServerOptions.default

  def toRoute(se: ServerEndpoint[Any, Future]): FinatraRoute = {
    val serverInterpreter = new ServerInterpreter[Any, Future, FinatraContent, NoStreams](
      _ => List(se),
      new FinatraRequestBody(finatraServerOptions),
      new FinatraToResponseBody,
      finatraServerOptions.interceptors,
      finatraServerOptions.deleteFile
    )(FutureMonadError, new FinatraBodyListener[Future]())

    val handler = {
      request: Request =>
        val serverRequest = new FinatraServerRequest(request)

        serverInterpreter(serverRequest).map {
          case RequestResult.Failure(_) => Response(Status.NotFound)
          case RequestResult.Response(response) =>
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

            response.headers.foreach(header => responseWithContent.headerMap.add(header.name, header.value))

            // If there's a content-type header in headers, override the content-type.
            response.contentType.foreach(ct => responseWithContent.contentType = ct)

            responseWithContent
        }
    }

    FinatraRoute(handler, httpMethod(se.endpoint), path(se.securityInput.and(se.input)))
  }

  private[finatra] def path(input: EndpointInput[_]): String = {
    val basicInputs = input.asVectorOfBasicInputs()
    val p = basicInputs.collect {
      case segment: EndpointInput.FixedPath[_] => segment.show
      case PathCapture(Some(name), _, _)       => s"/:$name"
      case PathCapture(_, _, _)                => "/:param"
      case EndpointInput.PathsCapture(_, _)    => "/:*"
    }.mkString
    if (p.isEmpty) "/:*"
    // checking if there's an input which rejects trailing slashes; otherwise the default behavior is to accept them
    else if (
      basicInputs.exists {
        case i: EndpointInput.ExtractFromRequest[_] if i.attribute(NoTrailingSlash.Attribute).getOrElse(false) => true
        case _                                                                                                 => false
      }
    ) p + "/?"
    else p
  }

  private[finatra] def httpMethod(endpoint: AnyEndpoint): Method = endpoint.method.map(m => Method(m.method)).getOrElse(Method("ANY"))
}

object FinatraServerInterpreter {
  def apply(serverOptions: FinatraServerOptions = FinatraServerOptions.default): FinatraServerInterpreter = {
    new FinatraServerInterpreter {
      override def finatraServerOptions: FinatraServerOptions = serverOptions
    }
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
