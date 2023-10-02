package sttp.tapir.server.pekkohttp

import org.apache.pekko.http.scaladsl.model.{MediaType => _}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server._
import org.apache.pekko.http.scaladsl.server.directives.RouteDirectives
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.monad.FutureMonad
import sttp.tapir._
import sttp.tapir.server.{ServerDefaults, ServerEndpoint}

import scala.concurrent.Future
import scala.util.{Failure, Success}

private[pekkohttp] class EndpointToPekkoServer(serverOptions: PekkoHttpServerOptions) {

  /** Converts the endpoint to a directive that -for matching requests- decodes the input parameters
    * and provides those input parameters and a function. The function can be called to complete the request.
    *
    * Example usage:
    * {{{
    * def logic(input: I): Future[Either[E, O] = ???
    *
    * endpoint.toDirective { (input, completion) =>
    *   securityDirective {
    *     completion(logic(input))
    *   }
    * }
    * }}}
    *
    * If type `I` is a tuple, and `logic` has 1 parameter per tuple member, use {{{completion((logic _).tupled(input))}}}
    */
  def toDirective[I, E, O](e: Endpoint[I, E, O, PekkoStreams with WebSockets]): Directive[(I, Future[Either[E, O]] => Route)] = {
    toDirective1(e).flatMap { (values: I) =>
      extractLog.flatMap { log =>
        extractExecutionContext.flatMap { implicit ec =>
          extractMaterializer.flatMap { implicit mat =>
            val completion: Future[Either[E, O]] => Route = result =>
              onComplete(result) {
                case Success(Left(v))  => new OutputToPekkoRoute().apply(ServerDefaults.StatusCodes.error.code, e.errorOutput, v)
                case Success(Right(v)) => new OutputToPekkoRoute().apply(ServerDefaults.StatusCodes.success.code, e.output, v)
                case Failure(t) =>
                  serverOptions.logRequestHandling.logicException(e, t)(log)
                  throw t
              }
            tprovide((values, completion))
          }
        }
      }
    }
  }

  def toRoute[I, E, O](se: ServerEndpoint[I, E, O, PekkoStreams with WebSockets, Future]): Route = {
    toDirective1(se.endpoint) { values =>
      extractLog { log =>
        mapResponse(resp => { serverOptions.logRequestHandling.requestHandled(se.endpoint, resp.status.intValue())(log); resp }) {
          extractExecutionContext { implicit ec =>
            extractMaterializer { implicit mat =>
              onComplete(se.logic(new FutureMonad)(values)) {
                case Success(Left(v))  => new OutputToPekkoRoute().apply(ServerDefaults.StatusCodes.error.code, se.endpoint.errorOutput, v)
                case Success(Right(v)) => new OutputToPekkoRoute().apply(ServerDefaults.StatusCodes.success.code, se.endpoint.output, v)
                case Failure(e) =>
                  serverOptions.logRequestHandling.logicException(se.endpoint, e)(log)
                  throw e
              }
            }
          }
        }
      }
    }
  }

  def toRoute(serverEndpoints: List[ServerEndpoint[_, _, _, PekkoStreams with WebSockets, Future]]): Route = {
    serverEndpoints.map(se => toRoute(se)).foldLeft(RouteDirectives.reject: Route)(_ ~ _)
  }

  private def toDirective1[I, E, O](e: Endpoint[I, E, O, PekkoStreams with WebSockets]): Directive1[I] = new EndpointToPekkoDirective(
    serverOptions
  )(e)
}
