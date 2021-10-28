package sttp.tapir.ztapir

import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import zio.{RIO, ZIO}

trait ZTapir {
  type ZServerEndpoint[R, A, U, I, E, O, -C] = ServerEndpoint[A, U, I, E, O, C, RIO[R, *]]

  implicit class RichZEndpoint[A, I, E, O, C](e: Endpoint[A, I, E, O, C]) {

    /** Combine this public endpoint description with a function, which implements the server-side logic. The logic returns a result, which
      * is either an error or a successful output, wrapped in an effect type `F`. For secure endpoints, use [[zServerSecurityLogic]].
      *
      * A server endpoint can be passed to a server interpreter. Each server interpreter supports effects of a specific type(s).
      *
      * Both the endpoint and logic function are considered complete, and cannot be later extended through the returned [[ServerEndpoint]]
      * value (except for endpoint meta-data). Secure endpoints allow providing the security logic before all the inputs and outputs are
      * specified.
      */
    def zServerLogic[R](logic: I => ZIO[R, E, O])(implicit aIsUnit: A =:= Unit): ZServerEndpoint[R, Unit, Unit, I, E, O, C] =
      ServerEndpoint.public(e.asInstanceOf[Endpoint[Unit, I, E, O, C]], _ => logic(_).either.resurrect)

    /** Combine this endpoint description with a function, which implements the security logic of the endpoint.
      *
      * Subsequently, the endpoint inputs and outputs can be extended (but not error outputs!). Then the main server logic can be provided,
      * given a function which accepts as arguments the result of the security logic and the remaining input. The final result is then a
      * [[ServerEndpoint]].
      *
      * A complete server endpoint can be passed to a server interpreter. Each server interpreter supports effects of a specific type(s).
      *
      * An example use-case is defining an endpoint with fully-defined errors, and with security logic built-in. Such an endpoint can be
      * then extended by multiple other endpoints, by specifying different inputs, outputs and the main logic.
      */
    def zServerSecurityLogic[R, U](f: A => ZIO[R, E, U]): ZPartialServerEndpoint[R, A, U, I, E, O, C] =
      ZPartialServerEndpoint(e, f)
  }

  implicit class RichZServiceEndpoint[R, A, U, I, E, O, C](zse: ZServerEndpoint[R, A, U, I, E, O, C]) {

    /** Extends the environment so that it can be made uniform across multiple endpoints. */
    def widen[R2 <: R]: ZServerEndpoint[R2, A, U, I, E, O, C] = zse.asInstanceOf[ZServerEndpoint[R2, A, U, I, E, O, C]] // this is fine
  }
}
