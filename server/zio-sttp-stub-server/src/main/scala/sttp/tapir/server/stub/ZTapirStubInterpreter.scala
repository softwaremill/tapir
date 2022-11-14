package sttp.tapir.server.stub

import sttp.client3.{Request, SttpBackend}
import sttp.client3.testing.SttpBackendStub
import sttp.monad.MonadError
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}
import sttp.tapir.ztapir._
import zio._

class ZTapirStubInterpreter[C, R, OPTIONS](
    private val endpoints: List[ZServerEndpoint[R, C]],
    private val interceptors: List[Interceptor[RIO[R, *]]],
    private val stub: SttpBackendStub[RIO[R, *], C]
) {

  // type F[_] = RIO[R, _]
  private implicit val monad: MonadError[RIO[R, *]] = stub.responseMonad

  def whenEndpoint[I, E, O](endpoint: Endpoint[_, I, E, O, _]): ZTapirEndpointStub[I, E, O] = new ZTapirEndpointStub(endpoint)

  def whenServerEndpoint[A, U, I, E, O](serverEndpoint: ZServerEndpoint[R, C]) = new ZTapirServerEndpointStub(
    serverEndpoint
  )

  def whenServerEndpointRunLogic(serverEndpoint: ZServerEndpoint[R, C]): ZTapirStubInterpreter[C, R, OPTIONS] = append(serverEndpoint)

  def whenServerEndpointsRunLogic(serverEndpoints: List[ZServerEndpoint[R, C]]): ZTapirStubInterpreter[C, R, OPTIONS] =
    serverEndpoints.foldLeft(this) { case (stub, sep) => stub.append(sep) }

  /** Returns `SttpBackend` which handles sent requests using a `ServerInterpreter`.
    */
  def backend(): SttpBackend[RIO[R, *], C] =
    stub.whenAnyRequest.thenRespondF((req: Request[_, _]) =>
      StubServerInterpreter(req, endpoints, RejectInterceptor.disableWhenSingleEndpoint(interceptors, endpoints))
    )

  class ZTapirEndpointStub[I, E, O](ep: Endpoint[_, I, E, O, _]) {
    def thenRespond(response: O): ZTapirStubInterpreter[C, R, OPTIONS] =
      append(publicEndpoint(logic = _ => ZIO.succeed(response)))

    def thenRespondError(errorResponse: E): ZTapirStubInterpreter[C, R, OPTIONS] =
      append(publicEndpoint(logic = _ => ZIO.fail(errorResponse)))

    def thenThrowException(ex: Throwable): ZTapirStubInterpreter[C, R, OPTIONS] =
      append(publicEndpoint(logic = _ => ZIO.fail(ex).orDie))

    def publicEndpoint(logic: I => ZIO[R, E, O]): ZServerEndpoint[R, C] =
      sttp.tapir.endpoint.in(ep.input).out(ep.output).errorOut(ep.errorOutput).zServerLogic(logic)

  }

  class ZTapirServerEndpointStub(val sep: ZServerEndpoint[R, C]) {
    def thenRespond(response: sep.OUTPUT, runSecurityLogic: Boolean = true): ZTapirStubInterpreter[C, R, OPTIONS] =
      if (runSecurityLogic)
        append(securedEndpoint(ZIO.succeed(response)))
      else new ZTapirEndpointStub(sep.endpoint).thenRespond(response)

    def thenRespondError(errorResponse: sep.ERROR_OUTPUT, runSecurityLogic: Boolean = true): ZTapirStubInterpreter[C, R, OPTIONS] =
      if (runSecurityLogic)
        append(securedEndpoint(ZIO.fail(errorResponse)))
      else new ZTapirEndpointStub(sep.endpoint).thenRespondError(errorResponse)

    def thenThrowException(ex: Throwable, runSecurityLogic: Boolean = true): ZTapirStubInterpreter[C, R, OPTIONS] = {
      if (runSecurityLogic)
        append(securedEndpoint(ZIO.fail(ex).orDie))
      new ZTapirEndpointStub(sep.endpoint).thenThrowException(ex)
    }

    def thenRunLogic(): ZTapirStubInterpreter[C, R, OPTIONS] = append(sep)

    def securedEndpoint(logic: ZIO[R, sep.ERROR_OUTPUT, sep.OUTPUT]): ZServerEndpoint[R, C] =
      ServerEndpoint[sep.SECURITY_INPUT, sep.PRINCIPAL, sep.INPUT, sep.ERROR_OUTPUT, sep.OUTPUT, C, RIO[R, *]](
        sep.endpoint,
        sep.securityLogic,
        _ => _ => _ => logic.either
      )
  }

  private def append(sep: ZServerEndpoint[R, C]) = new ZTapirStubInterpreter[C, R, OPTIONS](endpoints :+ sep, interceptors, stub)

}

object ZTapirStubInterpreter {

  def apply[C, R](stub: SttpBackendStub[RIO[R, *], C]): ZTapirStubInterpreter[C, R, Unit] =
    new ZTapirStubInterpreter[C, R, Unit](endpoints = List.empty, new CustomiseInterceptors[RIO[R, *], Any](_ => ()).interceptors, stub)

  def apply[C, R, O](options: CustomiseInterceptors[RIO[R, *], O], stub: SttpBackendStub[RIO[R, *], C]): ZTapirStubInterpreter[C, R, O] =
    new ZTapirStubInterpreter[C, R, O](endpoints = List.empty, options.interceptors, stub)

  def apply[C, R](interceptors: List[Interceptor[RIO[R, *]]], stub: SttpBackendStub[RIO[R, *], C]): ZTapirStubInterpreter[C, R, Any] =
    new ZTapirStubInterpreter[C, R, Any](endpoints = List.empty, interceptors, stub)
}
