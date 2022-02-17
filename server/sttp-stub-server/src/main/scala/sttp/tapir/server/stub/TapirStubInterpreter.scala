package sttp.tapir.server.stub

import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Request, Response, SttpBackend}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}

class TapirStubInterpreter[F[_], R, OPTIONS](
    private val endpoints: List[ServerEndpoint[R, F]],
    private val interceptors: List[Interceptor[F]]
)(implicit monad: MonadError[F]) {

  def whenEndpoint[I, E, O](endpoint: Endpoint[_, I, E, O, _]): TapirEndpointStub[I, E, O] = new TapirEndpointStub(endpoint)

  def whenServerEndpoint[A, U, I, E, O](serverEndpoint: ServerEndpoint.Full[A, U, I, E, O, R, F]) = new TapirServerEndpointStub(
    serverEndpoint
  )

  def whenServerEndpointRunLogic(serverEndpoint: ServerEndpoint[R, F]): TapirStubInterpreter[F, R, OPTIONS] = append(serverEndpoint)

  def whenServerEndpointsRunLogic(serverEndpoints: List[ServerEndpoint[R, F]]): TapirStubInterpreter[F, R, OPTIONS] =
    serverEndpoints.foldLeft(this) { case (stub, sep) => stub.append(sep) }

  /** Returns `SttpBackend` which runs `ServerInterpreter` on each request */
  def backend(): SttpBackend[F, R] = {
    new SttpBackend[F, R] {
      override def send[T, P](request: Request[T, P]): F[Response[T]] = {
        // SttpBackendStub is used to send request since it adjusts response to the shape described by request
        new SttpBackendStub[F, P](monad, { case _ => StubServerInterpreter(request, endpoints, interceptors) }, None).send(request)
      }

      override def close(): F[Unit] = monad.unit(())
      override def responseMonad: MonadError[F] = monad
    }
  }

  class TapirEndpointStub[I, E, O](ep: Endpoint[_, I, E, O, _]) {
    def respond(response: O): TapirStubInterpreter[F, R, OPTIONS] =
      append(publicEndpoint(logic = _ => _ => (Right(response): Either[E, O]).unit))

    def respondError(errorResponse: E): TapirStubInterpreter[F, R, OPTIONS] =
      append(publicEndpoint(logic = _ => _ => (Left(errorResponse): Either[E, O]).unit))

    def throwException(ex: Throwable): TapirStubInterpreter[F, R, OPTIONS] =
      append(publicEndpoint(logic = _ => _ => throw ex))

    private def publicEndpoint(logic: MonadError[F] => I => F[Either[E, O]]): ServerEndpoint[R, F] =
      ServerEndpoint.public[I, E, O, R, F](
        sttp.tapir.endpoint.in(ep.input).out(ep.output).errorOut(ep.errorOutput).asInstanceOf[Endpoint[Unit, I, E, O, R]],
        logic
      )
  }

  class TapirServerEndpointStub[A, U, I, E, O](sep: ServerEndpoint.Full[A, U, I, E, O, R, F]) {
    def respond(response: O, runSecurityLogic: Boolean = true): TapirStubInterpreter[F, R, OPTIONS] =
      if (runSecurityLogic)
        append(securedEndpoint(logic = _ => _ => _ => (Right(response): Either[E, O]).unit))
      else new TapirEndpointStub(sep.endpoint).respond(response)

    def respondError(errorResponse: E, runSecurityLogic: Boolean = true): TapirStubInterpreter[F, R, OPTIONS] =
      if (runSecurityLogic)
        append(securedEndpoint(logic = _ => _ => _ => (Left(errorResponse): Either[E, O]).unit))
      else new TapirEndpointStub(sep.endpoint).respondError(errorResponse)

    def throwException(ex: Throwable, runSecurityLogic: Boolean = true): TapirStubInterpreter[F, R, OPTIONS] = {
      if (runSecurityLogic)
        append(securedEndpoint(logic = _ => _ => _ => throw ex))
      new TapirEndpointStub(sep.endpoint).throwException(ex)
    }

    def runLogic(): TapirStubInterpreter[F, R, OPTIONS] = append(sep)

    private def securedEndpoint(logic: MonadError[F] => U => I => F[Either[E, O]]): ServerEndpoint[R, F] =
      ServerEndpoint[A, U, I, E, O, R, F](
        sep.endpoint,
        sep.securityLogic,
        logic
      )
  }

  private def append(sep: ServerEndpoint[R, F]) = new TapirStubInterpreter[F, R, OPTIONS](endpoints :+ sep, interceptors)
}

object TapirStubInterpreter {
  def apply[F[_], R](monad: MonadError[F]): TapirStubInterpreter[F, R, Unit] =
    new TapirStubInterpreter[F, R, Unit](endpoints = List.empty, new CustomInterceptors[F, Any](_ => ()).interceptors)(monad)

  def apply[F[_], R, O](options: CustomInterceptors[F, O], monad: MonadError[F]): TapirStubInterpreter[F, R, O] =
    new TapirStubInterpreter[F, R, O](endpoints = List.empty, options.interceptors)(monad)

  def apply[F[_], R](interceptors: List[Interceptor[F]], monad: MonadError[F]): TapirStubInterpreter[F, R, Any] =
    new TapirStubInterpreter[F, R, Any](endpoints = List.empty, interceptors)(monad)
}
