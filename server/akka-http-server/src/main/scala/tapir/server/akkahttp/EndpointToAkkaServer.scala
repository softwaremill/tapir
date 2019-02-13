package tapir.server.akkahttp

import akka.http.scaladsl.model.{HttpResponse, StatusCode => AkkaStatusCode, MediaType => _}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.util.{Tuple => AkkaTuple}
import tapir._
import tapir.server.StatusMapper
import tapir.typelevel.{ParamsAsArgs, ParamsToTuple}

import scala.concurrent.Future

class EndpointToAkkaServer(serverOptions: AkkaHttpServerOptions) {
  def toDirective[I, E, O, T](e: Endpoint[I, E, O, AkkaStream])(implicit paramsToTuple: ParamsToTuple.Aux[I, T]): Directive[T] = {
    implicit val tIsAkkaTuple: AkkaTuple[T] = AkkaTuple.yes
    toDirective1(e).flatMap { values =>
      tprovide(paramsToTuple.toTuple(values))
    }
  }

  def toRoute[I, E, O, FN[_]](e: Endpoint[I, E, O, AkkaStream])(
      logic: FN[Future[Either[E, O]]],
      statusMapper: StatusMapper[O],
      errorStatusMapper: StatusMapper[E])(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Route = {
    toDirective1(e) { values =>
      onSuccess(paramsAsArgs.applyFn(logic, values)) {
        case Left(v)  => outputToRoute(errorStatusMapper(v), e.errorOutput, v)
        case Right(v) => outputToRoute(statusMapper(v), e.output, v)
      }
    }
  }

  private def toDirective1[I, E, O](e: Endpoint[I, E, O, AkkaStream]): Directive1[I] = new EndpointToAkkaDirective(serverOptions)(e)

  private def outputToRoute[O](statusCode: AkkaStatusCode, output: EndpointIO[O], v: O): Route = {
    val responseValues = OutputToAkkaResponse(output, v)

    val completeRoute = responseValues.body match {
      case Some(entity) => complete(HttpResponse(entity = entity, status = statusCode))
      case None         => complete(HttpResponse(statusCode))
    }

    if (responseValues.headers.nonEmpty) {
      respondWithHeaders(responseValues.headers: _*)(completeRoute)
    } else {
      completeRoute
    }
  }
}
