package sttp.tapir

import sttp.tapir.server.ServerEndpoint
import sttp.tapir.typelevel.ParamSubtract
import zio.{RIO, ZIO}
import sttp.tapir.internal._

package object ztapir extends Tapir {
  type ZEndpoint[I, E, O] = Endpoint[I, E, O, Any]
  type ZServerEndpoint[R, I, E, O] = ServerEndpoint[I, E, O, Any, RIO[R, *]]

  implicit class RichZEndpoint[I, E, O](e: ZEndpoint[I, E, O]) {
    def zServerLogic[R](logic: I => ZIO[R, E, O]): ZServerEndpoint[R, I, E, O] = ServerEndpoint(e, _ => logic(_).either)

    /**
      * Combine this endpoint description with a function, which implements a part of the server-side logic.
      *
      * Subsequent parts of the logic can be provided later using [[ZServerEndpointInParts.andThenPart]], consuming
      * successive input parts. Finally, the logic can be completed, given a function which accepts as arguments the
      * results of applying on part-functions, and the remaining input. The final result is then a [[ZServerEndpoint]].
      *
      * A complete server endpoint can be passed to a server interpreter. Each server interpreter supports effects
      * of a specific type(s).
      *
      * When using this method, the endpoint description is considered complete, and cannot be later extended through
      * the returned [[ZServerEndpointInParts]] value. However, each part of the server logic can consume only a part
      * of the input. To provide the logic in parts, while still being able to extend the endpoint description, see
      * [[zServerLogicForCurrent]].
      *
      * An example use-case is providing authorization logic, followed by server logic (using an authorized user), given
      * a complete endpoint description.
      *
      * Note that the type of the `f` partial server logic function cannot be inferred, it must be explicitly given
      * (e.g. by providing a function or method value).
      */
    def zServerLogicPart[R, T, J, U](
        f: T => ZIO[R, E, U]
    )(implicit iMinusT: ParamSubtract.Aux[I, T, J]): ZServerEndpointInParts[R, U, J, I, E, O] = {
      type _T = T
      new ZServerEndpointInParts[R, U, J, I, E, O](e) {
        override type T = _T
        override def splitInput: I => (T, J) = i => split(i)(iMinusT)
        override def logicFragment: _T => ZIO[R, E, U] = f
      }
    }

    /**
      * Combine this endpoint description with a function, which implements a part of the server-side logic, for the
      * entire input defined so far.
      *
      * Subsequently, the endpoint inputs and outputs can be extended (but not error outputs!). Then, either further
      * parts of the server logic can be provided (again, consuming the whole input defined so far). Or, the entire
      * remaining server logic can be provided, given a function which accepts as arguments the results of applying
      * the part-functions, and the remaining input. The final result is then a [[ZServerEndpoint]].
      *
      * A complete server endpoint can be passed to a server interpreter. Each server interpreter supports effects
      * of a specific type(s).
      *
      * When using this method, each logic part consumes the whole input defined so far. To provide the server logic
      * in parts, where only part of the input is consumed (but the endpoint cannot be later extended), see the
      * [[zServerLogicPart]] function.
      *
      * An example use-case is defining an endpoint with fully-defined errors, and with authorization logic built-in.
      * Such an endpoint can be then extended by multiple other endpoints.
      */
    def zServerLogicForCurrent[R, U](f: I => ZIO[R, E, U]): ZPartialServerEndpoint[R, U, Unit, E, O] =
      new ZPartialServerEndpoint[R, U, Unit, E, O](e.copy(input = emptyInput)) {
        override type T = I
        override def tInput: EndpointInput[T] = e.input
        override def partialLogic: T => ZIO[R, E, U] = f
      }
  }
}
