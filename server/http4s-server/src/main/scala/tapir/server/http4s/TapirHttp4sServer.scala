package tapir.server.http4s

import cats.Monad
import cats.implicits._
import cats.effect.{ContextShift, Sync}
import org.http4s.{EntityBody, HttpRoutes}
import tapir.Endpoint
import tapir.internal.{ParamsToSeq, SeqToParams}
import tapir.typelevel.{ParamsAsArgs, ReplaceFirstInFn}

trait TapirHttp4sServer {
  implicit class RichHttp4sHttpEndpoint[I, E, O, F[_]](e: Endpoint[I, E, O, EntityBody[F]]) {
    def toRoutes[FN[_]](logic: FN[F[Either[E, O]]])(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN],
                                                    serverOptions: Http4sServerOptions[F],
                                                    fs: Sync[F],
                                                    fcs: ContextShift[F]): HttpRoutes[F] = {
      new EndpointToHttp4sServer(serverOptions).toRoutes(e)(logic)
    }
  }

  implicit class RichToMonadFunction[T, E, U, F[_]: Monad](f: T => F[Either[E, U]]) {
    def andThenRight[O, FN_U[_], FN_T[_]](g: FN_U[F[Either[E, O]]])(implicit
                                                                    r: ReplaceFirstInFn[U, FN_U, T, FN_T]): FN_T[F[Either[E, O]]] = {

      r.paramsAsArgsJk.toFn { paramsWithT =>
        val paramsWithTSeq = ParamsToSeq(paramsWithT)
        val t = paramsWithTSeq.head.asInstanceOf[T]
        f(t).flatMap {
          case Left(e) => implicitly[Monad[F]].point(Left(e))
          case Right(u) =>
            val paramsWithU = SeqToParams(u +: paramsWithTSeq.tail)
            r.paramsAsArgsIk.asInstanceOf[ParamsAsArgs.Aux[Any, FN_U]].applyFn(g, paramsWithU)
        }
      }
    }
  }
}
