package tapir.server.akkahttp

import akka.http.scaladsl.server.{Directive, Route}
import tapir.typelevel.{ParamsAsArgs, ParamsToTuple, ReplaceFirstInFn}
import tapir.Endpoint
import tapir.internal.{ParamsToSeq, SeqToParams}

import scala.concurrent.{ExecutionContext, Future}

trait TapirAkkaHttpServer {
  implicit class RichAkkaHttpEndpoint[I, E, O](e: Endpoint[I, E, O, AkkaStream]) {
    def toDirective[T](implicit paramsToTuple: ParamsToTuple.Aux[I, T], akkaHttpOptions: AkkaHttpServerOptions): Directive[T] =
      new EndpointToAkkaServer(akkaHttpOptions).toDirective(e)

    def toRoute[FN[_]](logic: FN[Future[Either[E, O]]])(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN],
                                                        serverOptions: AkkaHttpServerOptions): Route =
      new EndpointToAkkaServer(serverOptions).toRoute(e)(logic)
  }

  implicit class RichToFutureFunction[T, E, U](f: T => Future[Either[E, U]])(implicit ec: ExecutionContext) {
    def andThenRight[O, FN_U[_], FN_T[_]](g: FN_U[Future[Either[E, O]]])(
        implicit
        r: ReplaceFirstInFn[U, FN_U, T, FN_T]): FN_T[Future[Either[E, O]]] = {

      r.paramsAsArgsJk.toFn { paramsWithT =>
        val paramsWithTSeq = ParamsToSeq(paramsWithT)
        val t = paramsWithTSeq.head.asInstanceOf[T]
        f(t).flatMap {
          case Left(e) => Future.successful(Left(e))
          case Right(u) =>
            val paramsWithU = SeqToParams(u +: paramsWithTSeq.tail)
            r.paramsAsArgsIk.asInstanceOf[ParamsAsArgs.Aux[Any, FN_U]].applyFn(g, paramsWithU)
        }
      }
    }
  }
}
