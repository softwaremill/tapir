package sttp.tapir.server.stub

import sttp.client3.SttpBackend
import sttp.client3.testing.SttpBackendStub
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}

class TapirStubInterpreter[F[_], R, OPTIONS](
    private val endpoints: List[ServerEndpoint[R, F]],
    private val interceptors: List[Interceptor[F]],
    private val stub: SttpBackendStub[F, R]
) {

  def whenEndpoint[I, E, O](endpoint: Endpoint[_, I, E, O, _]): TapirEndpointStub[I, E, O] = new TapirEndpointStub(endpoint)

  def whenServerEndpoint[A, U, I, E, O](serverEndpoint: ServerEndpoint.Full[A, U, I, E, O, R, F]) = new TapirServerEndpointStub(
    serverEndpoint
  )

  def whenServerEndpointRunLogic(serverEndpoint: ServerEndpoint[R, F]): TapirStubInterpreter[F, R, OPTIONS] = append(serverEndpoint)

  def whenServerEndpointsRunLogic(serverEndpoints: List[ServerEndpoint[R, F]]): TapirStubInterpreter[F, R, OPTIONS] =
    serverEndpoints.foldLeft(this) { case (stub, sep) => stub.append(sep) }

  /** Returns `SttpBackend` which handles sent requests using a `ServerInterpreter`. */
  def backend(): SttpBackend[F, R] =
    stub.whenAnyRequest.thenRespondF(req =>
      StubServerInterpreter(req, endpoints, RejectInterceptor.disableWhenSingleEndpoint(interceptors, endpoints))
    )

  class TapirEndpointStub[I, E, O](ep: Endpoint[_, I, E, O, _]) {
    def thenRespond(response: O): TapirStubInterpreter[F, R, OPTIONS] =
      append(publicEndpoint(logic = _ => _ => (Right(response): Either[E, O]).unit))

    def thenRespondError(errorResponse: E): TapirStubInterpreter[F, R, OPTIONS] =
      append(publicEndpoint(logic = _ => _ => (Left(errorResponse): Either[E, O]).unit))

    def thenThrowException(ex: Throwable): TapirStubInterpreter[F, R, OPTIONS] =
      append(publicEndpoint(logic = _ => _ => throw ex))

    private def publicEndpoint(logic: MonadError[F] => I => F[Either[E, O]]): ServerEndpoint[R, F] =
      ServerEndpoint.public[I, E, O, R, F](
        sttp.tapir.endpoint.in(ep.input).out(ep.output).errorOut(ep.errorOutput).asInstanceOf[Endpoint[Unit, I, E, O, R]],
        logic
      )
  }

  class TapirServerEndpointStub[A, U, I, E, O](sep: ServerEndpoint.Full[A, U, I, E, O, R, F]) {
    def thenRespond(response: O, runSecurityLogic: Boolean = true): TapirStubInterpreter[F, R, OPTIONS] =
      if (runSecurityLogic)
        append(securedEndpoint(logic = _ => _ => _ => (Right(response): Either[E, O]).unit))
      else new TapirEndpointStub(sep.endpoint).thenRespond(response)

    def thenRespondError(errorResponse: E, runSecurityLogic: Boolean = true): TapirStubInterpreter[F, R, OPTIONS] =
      if (runSecurityLogic)
        append(securedEndpoint(logic = _ => _ => _ => (Left(errorResponse): Either[E, O]).unit))
      else new TapirEndpointStub(sep.endpoint).thenRespondError(errorResponse)

    def thenThrowException(ex: Throwable, runSecurityLogic: Boolean = true): TapirStubInterpreter[F, R, OPTIONS] = {
      if (runSecurityLogic)
        append(securedEndpoint(logic = _ => _ => _ => throw ex))
      else new TapirEndpointStub(sep.endpoint).thenThrowException(ex)
    }

    def thenRunLogic(): TapirStubInterpreter[F, R, OPTIONS] = append(sep)

    private def securedEndpoint(logic: MonadError[F] => U => I => F[Either[E, O]]): ServerEndpoint[R, F] =
      ServerEndpoint[A, U, I, E, O, R, F](
        sep.endpoint,
        sep.securityLogic,
        logic
      )
  }

  private def append(sep: ServerEndpoint[R, F]) = new TapirStubInterpreter[F, R, OPTIONS](endpoints :+ sep, interceptors, stub)

  private implicit val monad: MonadError[F] = stub.responseMonad
}

object TapirStubInterpreter {
  def apply[F[_], R](stub: SttpBackendStub[F, R]): TapirStubInterpreter[F, R, Unit] =
    new TapirStubInterpreter[F, R, Unit](endpoints = List.empty, new CustomiseInterceptors[F, Any](_ => ()).interceptors, stub)

  def apply[F[_], R, O](options: CustomiseInterceptors[F, O], stub: SttpBackendStub[F, R]): TapirStubInterpreter[F, R, O] =
    new TapirStubInterpreter[F, R, O](endpoints = List.empty, options.interceptors, stub)

  def apply[F[_], R](interceptors: List[Interceptor[F]], stub: SttpBackendStub[F, R]): TapirStubInterpreter[F, R, Any] =
    new TapirStubInterpreter[F, R, Any](endpoints = List.empty, interceptors, stub)
}
