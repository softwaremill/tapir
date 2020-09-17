package sttp.tapir.server.akkahttp

import akka.http.scaladsl.model.{MediaType => _}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.RouteDirectives
import sttp.tapir._
import sttp.tapir.server.{ServerDefaults, ServerEndpoint}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class EndpointToAkkaServer(serverOptions: AkkaHttpServerOptions) {

  /**
    * Converts the endpoint to a directive that -for matching requests- decodes the input parameters
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
  def toDirective[I, E, O](e: Endpoint[I, E, O, AkkaStream]): Directive[(I, Future[Either[E, O]] => Route)] = {
    toDirective1(e).flatMap { (values: I) =>
      extractLog.flatMap { log =>
        val completion: Future[Either[E, O]] => Route = result =>
          onComplete(result) {
            case Success(Left(v))  => OutputToAkkaRoute(ServerDefaults.StatusCodes.error.code, e.errorOutput, v)
            case Success(Right(v)) => OutputToAkkaRoute(ServerDefaults.StatusCodes.success.code, e.output, v)
            case Failure(t) =>
              serverOptions.logRequestHandling.logicException(e, t)(log)
              throw t
          }
        tprovide((values, completion))
      }
    }
  }

  def toRoute[I, E, O](se: ServerEndpoint[I, E, O, AkkaStreams, Future]): Route = {
    toDirective1(se.endpoint) { values =>
      extractLog { log =>
        mapResponse(resp => { serverOptions.logRequestHandling.requestHandled(se.endpoint, resp.status.intValue())(log); resp }) {
          extractExecutionContext { ec =>
            onComplete(se.logic(new FutureMonad()(ec))(values)) {
              case Success(Left(v))  => OutputToAkkaRoute(ServerDefaults.StatusCodes.error.code, se.endpoint.errorOutput, v)
              case Success(Right(v)) => OutputToAkkaRoute(ServerDefaults.StatusCodes.success.code, se.endpoint.output, v)
              case Failure(e) =>
                serverOptions.logRequestHandling.logicException(se.endpoint, e)(log)
                throw e
            }
          }
        }
      }
    }
  }

  def toRoute(serverEndpoints: List[ServerEndpoint[_, _, _, AkkaStreams, Future]]): Route = {
    serverEndpoints.map(se => toRoute(se)).foldLeft(RouteDirectives.reject: Route)(_ ~ _)
  }

  private def toDirective1[I, E, O](e: Endpoint[I, E, O, AkkaStreams]): Directive1[I] = new EndpointToAkkaDirective(serverOptions)(e)
}
