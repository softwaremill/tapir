package sttp.tapir.server

import com.twitter.util.Future
import com.twitter.util.logging.Logging
import sttp.tapir.Endpoint

import scala.reflect.ClassTag

package object finatra {
  implicit class RichFinatraEndpoint[I, E, O](e: Endpoint[I, E, O, Any]) extends Logging {
    @deprecated("Use FinatraServerInterpreter.toRoute", since = "0.17.1")
    def toRoute(logic: I => Future[Either[E, O]])(implicit serverOptions: FinatraServerOptions): FinatraRoute =
      FinatraServerInterpreter.toRoute(e)(logic)

    @deprecated("Use FinatraServerInterpreter.toRouteRecoverErrors", since = "0.17.1")
    def toRouteRecoverErrors(logic: I => Future[O])(implicit
        eIsThrowable: E <:< Throwable,
        eClassTag: ClassTag[E]
    ): FinatraRoute = FinatraServerInterpreter.toRouteRecoverErrors(e)(logic)
  }

  implicit class RichFinatraServerEndpoint[I, E, O](e: ServerEndpoint[I, E, O, Any, Future]) extends Logging {
    @deprecated("Use FinatraServerInterpreter.toRoute", since = "0.17.1")
    def toRoute(implicit serverOptions: FinatraServerOptions): FinatraRoute = FinatraServerInterpreter.toRoute(e)
  }
}
