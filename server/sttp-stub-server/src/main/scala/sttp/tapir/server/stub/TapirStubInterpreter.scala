package sttp.tapir.server.stub

import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}

class TapirStubInterpreter[F[_], R, OPTIONS](private val stub: SttpBackendStub[F, R], private val interceptors: List[Interceptor[F]]) {

  def stubEndpoint[A, I, E, O](endpoint: Endpoint[A, I, E, O, _]): TapirEndpointStub[A, I, E, O] = new TapirEndpointStub(endpoint)
  def stubServerEndpoint(serverEndpoint: ServerEndpoint[R, F]): TapirServerEndpointStub = new TapirServerEndpointStub(serverEndpoint)
  def stubServerEndpointFull[A, U, I, E, O](serverEndpoint: ServerEndpoint.Full[A, U, I, E, O, R, F]) = new TapirServerEndpointFullStub(
    serverEndpoint
  )
  def backend(): SttpBackendStub[F, R] = stub

  class TapirEndpointStub[A, I, E, O](ep: Endpoint[A, I, E, O, _]) {
    def whenRequestMatches() = new WhenEndpointMatches(ep, richStub._whenRequestMatches(ep))

    def whenInputMatches(inputMatcher: I => Boolean) = new WhenEndpointMatches(ep, richStub._whenInputMatches(ep.input)(inputMatcher))

    def whenSecurityMatches(securityInputMatcher: A => Boolean) =
      new WhenEndpointMatches(ep, richStub._whenInputMatches(ep.securityInput)(securityInputMatcher))
  }

  class TapirServerEndpointStub(sep: ServerEndpoint[R, F]) {
    def whenRequestMatches() = new WhenServerEndpointMatches(sep, richStub._whenRequestMatches(sep.endpoint))
  }

  class TapirServerEndpointFullStub[A, U, I, E, O](sep: ServerEndpoint.Full[A, U, I, E, O, R, F]) {
    def whenRequestMatches() = new WhenServerEndpointFullMatches(sep, richStub._whenRequestMatches(sep.endpoint))

    def whenInputMatches(inputMatcher: I => Boolean) =
      new WhenServerEndpointFullMatches(sep, richStub._whenInputMatches(sep.input)(inputMatcher))

    def whenSecurityMatches(securityInputMatcher: A => Boolean) =
      new WhenServerEndpointFullMatches(sep, richStub._whenInputMatches(sep.securityInput)(securityInputMatcher))
  }

  class WhenEndpointMatches[A, I, E, O](ep: Endpoint[A, I, E, O, _], whenRequest: richStub.stub.WhenRequest) {
    def thenSuccess(response: O): TapirStubInterpreter[F, R, OPTIONS] = {
      // ??? create ServerEndpoint and invoke Interpreter?
      val successSep =
        ServerEndpoint.public[I, E, O, R, F](ep.asInstanceOf[Endpoint[Unit, I, E, O, R]], _ => _ => (Right(response): Either[E, O]).unit)
      val newStub = whenRequest.thenRespondF(req => new StubServerInterpreter(stub).interpretRequest(req, successSep, interceptors))
      new TapirStubInterpreter(newStub, interceptors)
    }

    def thenError(errorResponse: E, statusCode: StatusCode): TapirStubInterpreter[F, R, OPTIONS] = {
      // ??? create ServerEndpoint and invoke Interpreter?
      val errorSep = ServerEndpoint.public[I, E, O, R, F](
        ep.asInstanceOf[Endpoint[Unit, I, E, O, R]],
        _ => _ => (Left(errorResponse): Either[E, O]).unit
      )
      val newStub = whenRequest.thenRespondF(req =>
        new StubServerInterpreter(stub).interpretRequest(req, errorSep, interceptors).map(_.copy(code = statusCode))
      )
      new TapirStubInterpreter(newStub, interceptors)
    }

    def generic(f: richStub.stub.WhenRequest => SttpBackendStub[F, R]): TapirStubInterpreter[F, R, OPTIONS] = {
      val newStub = f(whenRequest)
      new TapirStubInterpreter(newStub, interceptors)
    }
  }

  class WhenServerEndpointMatches(sep: ServerEndpoint[R, F], whenRequest: richStub.stub.WhenRequest) {
    def thenLogic(): TapirStubInterpreter[F, R, OPTIONS] = {
      val newStub = whenRequest.thenRespondF(req => new StubServerInterpreter(stub).interpretRequest(req, sep, interceptors))
      new TapirStubInterpreter(newStub, interceptors)
    }
  }

  class WhenServerEndpointFullMatches[A, U, I, E, O](
      sep: ServerEndpoint.Full[A, U, I, E, O, R, F],
      whenRequest: richStub.stub.WhenRequest
  ) {
    def thenSuccess(response: O): TapirStubInterpreter[F, R, OPTIONS] = {
      val successSep = ServerEndpoint[A, U, I, E, O, R, F](
        sep.endpoint,
        sep.securityLogic,
        _ => _ => _ => (Right(response): Either[E, O]).unit
      )
      val newStub = whenRequest.thenRespondF(req => new StubServerInterpreter(stub).interpretRequest(req, successSep, interceptors))
      new TapirStubInterpreter(newStub, interceptors)
    }

    def thenError(errorResponse: E, statusCode: StatusCode): TapirStubInterpreter[F, R, OPTIONS] = {
      val errorSep = ServerEndpoint[A, U, I, E, O, R, F](
        sep.endpoint,
        sep.securityLogic,
        _ => _ => _ => (Left(errorResponse): Either[E, O]).unit
      )
      val newStub = whenRequest.thenRespondF(req =>
        new StubServerInterpreter(stub).interpretRequest(req, errorSep, interceptors).map(_.copy(code = statusCode))
      )
      new TapirStubInterpreter(newStub, interceptors)
    }

    def generic(f: richStub.stub.WhenRequest => SttpBackendStub[F, R]): TapirStubInterpreter[F, R, OPTIONS] = {
      val newStub = f(whenRequest)
      new TapirStubInterpreter(newStub, interceptors)
    }

    def thenLogic(): TapirStubInterpreter[F, R, OPTIONS] = {
      val newStub = whenRequest.thenRespondF(req => new StubServerInterpreter(stub).interpretRequest(req, sep, interceptors))
      new TapirStubInterpreter(newStub, interceptors)
    }
  }

  private val richStub = new RichSttpBackendStub[F, R](stub)
  private implicit val monadError: MonadError[F] = stub.responseMonad

}

object TapirStubInterpreter {
  def apply[F[_], R, O](stub: SttpBackendStub[F, R], options: CustomInterceptors[F, O]): TapirStubInterpreter[F, R, O] =
    new TapirStubInterpreter(stub, options.interceptors)

  def apply[F[_], R](stub: SttpBackendStub[F, R], interceptors: List[Interceptor[F]]): TapirStubInterpreter[F, R, Any] =
    new TapirStubInterpreter(stub, interceptors)
}
