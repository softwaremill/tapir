package sttp.tapir.server.finatra

import _root_.cats.effect.Effect
import com.twitter.inject.Logging
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint

import scala.reflect.ClassTag

package object cats {
  implicit class RichFinatraCatsEndpoint[I, E, O](e: Endpoint[I, E, O, Any]) extends Logging {
    @deprecated("Use FinatraCatsServerInterpreter.toRoute", since = "0.17.1")
    def toRoute[F[_]](logic: I => F[Either[E, O]])(implicit serverOptions: FinatraServerOptions, eff: Effect[F]): FinatraRoute =
      FinatraCatsServerInterpreter.toRoute(e)(logic)

    @deprecated("Use FinatraCatsServerInterpreter.toRouteRecoverErrors", since = "0.17.1")
    def toRouteRecoverErrors[F[_]](logic: I => F[O])(implicit
        eIsThrowable: E <:< Throwable,
        eClassTag: ClassTag[E],
        eff: Effect[F]
    ): FinatraRoute = FinatraCatsServerInterpreter.toRouteRecoverErrors(e)(logic)
  }

  implicit class RichFinatraCatsServerEndpoint[I, E, O, F[_]](e: ServerEndpoint[I, E, O, Any, F]) extends Logging {
    @deprecated("Use FinatraServerInterpreter.toRoute", since = "0.17.1")
    def toRoute(implicit serverOptions: FinatraServerOptions, eff: Effect[F]): FinatraRoute =
      FinatraCatsServerInterpreter.toRoute(e)
  }
}
