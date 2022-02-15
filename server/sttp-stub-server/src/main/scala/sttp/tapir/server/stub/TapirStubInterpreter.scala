package sttp.tapir.server.stub

import sttp.client3.testing.SttpBackendStub
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}

class TapirStubInterpreter[F[_], R, OPTIONS](
    private val endpoints: List[ServerEndpoint[R, F]],
    private val interceptors: List[Interceptor[F]],
    private val stub: SttpBackendStub[F, R]
) {

  def forEndpoint[I, E, O](endpoint: Endpoint[_, I, E, O, _]): TapirEndpointStub[I, E, O] = new TapirEndpointStub(endpoint)

  def forServerEndpoint[A, U, I, E, O](serverEndpoint: ServerEndpoint.Full[A, U, I, E, O, R, F]) = new TapirServerEndpointStub(
    serverEndpoint
  )

  def forServerEndpointRunLogic(serverEndpoint: ServerEndpoint[R, F]): TapirStubInterpreter[F, R, OPTIONS] =
    new TapirStubInterpreter(endpoints :+ serverEndpoint, interceptors, stub)

  def forServerEndpointsRunLogic(serverEndpoints: List[ServerEndpoint[R, F]]): TapirStubInterpreter[F, R, OPTIONS] =
    new TapirStubInterpreter(endpoints ++ serverEndpoints, interceptors, stub)

  /** Returns `SttpBackendStub` which runs `ServerInterpreter` on each request */
  def backend(): SttpBackendStub[F, R] =
    stub.whenAnyRequest.thenRespondF(req => TestServerInterpreter[F, R](req, endpoints, interceptors))

  class TapirEndpointStub[I, E, O](ep: Endpoint[_, I, E, O, _]) {
    def returnSuccess(response: O): TapirStubInterpreter[F, R, OPTIONS] =
      append(
        ServerEndpoint.public[I, E, O, R, F](ep.asInstanceOf[Endpoint[Unit, I, E, O, R]], _ => _ => (Right(response): Either[E, O]).unit)
      )

    def returnError(errorResponse: E): TapirStubInterpreter[F, R, OPTIONS] =
      append(
        ServerEndpoint.public[I, E, O, R, F](
          ep.asInstanceOf[Endpoint[Unit, I, E, O, R]],
          _ => _ => (Left(errorResponse): Either[E, O]).unit
        )
      )

    def throwException(ex: Throwable): TapirStubInterpreter[F, R, OPTIONS] =
      append(
        ServerEndpoint.public[I, E, O, R, F](
          ep.asInstanceOf[Endpoint[Unit, I, E, O, R]],
          _ => _ => throw ex
        )
      )
  }

  class TapirServerEndpointStub[A, U, I, E, O](sep: ServerEndpoint.Full[A, U, I, E, O, R, F]) {
    def returnSuccess(response: O, runAuthLogic: Boolean = true): TapirStubInterpreter[F, R, OPTIONS] =
      if (runAuthLogic) {
        append(
          ServerEndpoint[A, U, I, E, O, R, F](
            sep.endpoint,
            sep.securityLogic,
            _ => _ => _ => (Right(response): Either[E, O]).unit
          )
        )
      } else new TapirEndpointStub(sep.endpoint).returnSuccess(response)

    def returnError(errorResponse: E, runAuthLogic: Boolean = true): TapirStubInterpreter[F, R, OPTIONS] =
      if (runAuthLogic) {
        append(
          ServerEndpoint[A, U, I, E, O, R, F](
            sep.endpoint,
            sep.securityLogic,
            _ => _ => _ => (Left(errorResponse): Either[E, O]).unit
          )
        )
      } else new TapirEndpointStub(sep.endpoint).returnError(errorResponse)

    def throwException(ex: Throwable): TapirStubInterpreter[F, R, OPTIONS] = new TapirEndpointStub(sep.endpoint).throwException(ex)

    def runLogic(): TapirStubInterpreter[F, R, OPTIONS] = append(sep)
  }

  private def append(sep: ServerEndpoint[R, F]) = new TapirStubInterpreter[F, R, OPTIONS](endpoints :+ sep, interceptors, stub)

  private implicit val monadError: MonadError[F] = stub.responseMonad
}

object TapirStubInterpreter {
  def apply[F[_], R, O](options: CustomInterceptors[F, O], stub: SttpBackendStub[F, R]): TapirStubInterpreter[F, R, O] =
    new TapirStubInterpreter(endpoints = List.empty, options.interceptors, stub)

  def apply[F[_], R](interceptors: List[Interceptor[F]], stub: SttpBackendStub[F, R]): TapirStubInterpreter[F, R, Any] =
    new TapirStubInterpreter(endpoints = List.empty, interceptors, stub)
}
