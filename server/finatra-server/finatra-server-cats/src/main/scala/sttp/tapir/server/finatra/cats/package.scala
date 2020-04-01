package sttp.tapir.server.finatra

import _root_.cats.effect.Effect
import com.github.ghik.silencer.silent
import com.twitter.inject.Logging
import io.catbird.util.Rerunnable
import io.catbird.util.effect._
import sttp.tapir.Endpoint

import scala.reflect.ClassTag

package object cats {
  implicit class RichFinatraCatsEndpoint[I, E, O](e: Endpoint[I, E, O, Nothing]) extends Logging {
    def toRoute[F[_]](logic: I => F[Either[E, O]])(implicit serverOptions: FinatraServerOptions, eff: Effect[F]): FinatraRoute = {
      new RichFinatraEndpoint(e).toRoute(i => eff.toIO(logic(i)).to[Rerunnable].run)
    }

    @silent("never used")
    def toRouteRecoverErrors[F[_]](logic: I => F[O])(
        implicit eIsThrowable: E <:< Throwable,
        eClassTag: ClassTag[E],
        eff: Effect[F]
    ): FinatraRoute = {
      new RichFinatraEndpoint(e).toRoute { i: I =>
        eff.toIO(logic(i)).map(Right(_)).to[Rerunnable].run.handle {
          case ex if eClassTag.runtimeClass.isInstance(ex) => Left(ex.asInstanceOf[E])
        }
      }
    }
  }
}
