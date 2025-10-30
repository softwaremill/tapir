package sttp.tapir.ztapir

import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import zio.{RIO, ZIO}

trait ZTapir {
  type ZServerEndpoint[R, -C] = ServerEndpoint[C, RIO[R, *]]

  implicit class RichZEndpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, C](
      e: Endpoint[SECURITY_INPUT, INPUT, ERROR_OUTPUT, OUTPUT, C]
  ) {

    /** Combine this public endpoint description with a function, which implements the server-side logic. The logic returns a result, which
      * is either an error or a successful output, wrapped in an effect type `F`. For secure endpoints, use [[zServerSecurityLogic]].
      *
      * A server endpoint can be passed to a server interpreter. Each server interpreter supports effects of a specific type(s).
      *
      * Both the endpoint and logic function are considered complete, and cannot be later extended through the returned [[ServerEndpoint]]
      * value (except for endpoint meta-data). Secure endpoints allow providing the security logic before all the inputs and outputs are
      * specified.
      */
    def zServerLogic[R](logic: INPUT => ZIO[R, ERROR_OUTPUT, OUTPUT])(implicit
        aIsUnit: SECURITY_INPUT =:= Unit
    ): ServerEndpoint.Full[Unit, Unit, INPUT, ERROR_OUTPUT, OUTPUT, C, RIO[R, *]] =
      ServerEndpoint.public(e.asInstanceOf[Endpoint[Unit, INPUT, ERROR_OUTPUT, OUTPUT, C]], _ => logic(_: INPUT).either.resurrect)

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
    def zServerSecurityLogic[R, U](
        f: SECURITY_INPUT => ZIO[R, ERROR_OUTPUT, U]
    ): ZPartialServerEndpoint[R, SECURITY_INPUT, U, INPUT, ERROR_OUTPUT, OUTPUT, C] =
      ZPartialServerEndpoint(e, f)
  }

  implicit class RichZServerEndpoint[R, C](zse: ZServerEndpoint[R, C]) {

    /** Extends the environment so that it can be made uniform across multiple endpoints. */
    def widen[R2 <: R]: ZServerEndpoint[R2, C] = zse.asInstanceOf[ZServerEndpoint[R2, C]] // this is fine
  }
}
