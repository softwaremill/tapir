package sttp.tapir.server.stub4

import sttp.capabilities.WebSockets
import sttp.client4.testing.{
  AbstractBackendStub,
  BackendStub,
  StreamBackendStub,
  StubBody,
  SyncBackendStub,
  WebSocketBackendStub,
  WebSocketStreamBackendStub
}
import sttp.client4.{
  Backend,
  GenericBackend,
  GenericRequest,
  Response,
  StreamBackend,
  SyncBackend,
  WebSocketBackend,
  WebSocketStreamBackend
}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.shared.Identity
import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.{CustomiseInterceptors, Interceptor}

class TapirSyncStubInterpreter[OPTIONS](
    endpoints: List[ServerEndpoint[Any, Identity]],
    interceptors: List[Interceptor[Identity]],
    stub: SyncBackendStub
) extends AbstractTapirStubInterpreter[Identity, Any, OPTIONS](endpoints, interceptors, stub) {

  override type StubType = TapirSyncStubInterpreter[OPTIONS]

  override protected def append(sep: ServerEndpoint[Any, Identity]): StubType =
    new TapirSyncStubInterpreter[OPTIONS](endpoints :+ sep, interceptors, stub)

  override protected def thisAsStubType: StubType = this

  /** Returns a [[SyncBackend]] which handles sent requests using Tapir's [[ServerInterpreter]]. */
  override def backend(): SyncBackend = stub.whenAnyRequest.thenRespondF(handleRequest(_))
}

object TapirSyncStubInterpreter {
  def apply(): TapirSyncStubInterpreter[Unit] =
    new TapirSyncStubInterpreter[Unit](
      endpoints = List.empty,
      new CustomiseInterceptors[Identity, Any](_ => ()).interceptors,
      BackendStub.synchronous
    )

  def apply(stub: SyncBackendStub): TapirSyncStubInterpreter[Unit] =
    new TapirSyncStubInterpreter[Unit](endpoints = List.empty, new CustomiseInterceptors[Identity, Any](_ => ()).interceptors, stub)

  def apply[O](options: CustomiseInterceptors[Identity, O]): TapirSyncStubInterpreter[O] =
    new TapirSyncStubInterpreter[O](endpoints = List.empty, options.interceptors, BackendStub.synchronous)

  def apply[O](options: CustomiseInterceptors[Identity, O], stub: SyncBackendStub): TapirSyncStubInterpreter[O] =
    new TapirSyncStubInterpreter[O](endpoints = List.empty, options.interceptors, stub)

  def apply(interceptors: List[Interceptor[Identity]], stub: SyncBackendStub): TapirSyncStubInterpreter[Any] =
    new TapirSyncStubInterpreter[Any](endpoints = List.empty, interceptors, stub)
}

//

class TapirStubInterpreter[F[_], OPTIONS](
    endpoints: List[ServerEndpoint[Any, F]],
    interceptors: List[Interceptor[F]],
    stub: BackendStub[F]
) extends AbstractTapirStubInterpreter[F, Any, OPTIONS](endpoints, interceptors, stub) {

  override type StubType = TapirStubInterpreter[F, OPTIONS]

  override protected def append(sep: ServerEndpoint[Any, F]): StubType =
    new TapirStubInterpreter[F, OPTIONS](endpoints :+ sep, interceptors, stub)

  override protected def thisAsStubType: StubType = this

  /** Returns a [[Backend]] which handles sent requests using Tapir's [[ServerInterpreter]]. */
  override def backend(): Backend[F] = stub.whenAnyRequest.thenRespondF(handleRequest(_))
}

object TapirStubInterpreter {
  def apply[F[_]](stub: BackendStub[F]): TapirStubInterpreter[F, Unit] =
    new TapirStubInterpreter[F, Unit](endpoints = List.empty, new CustomiseInterceptors[F, Any](_ => ()).interceptors, stub)

  def apply[F[_], O](options: CustomiseInterceptors[F, O], stub: BackendStub[F]): TapirStubInterpreter[F, O] =
    new TapirStubInterpreter[F, O](endpoints = List.empty, options.interceptors, stub)

  def apply[F[_]](interceptors: List[Interceptor[F]], stub: BackendStub[F]): TapirStubInterpreter[F, Any] =
    new TapirStubInterpreter[F, Any](endpoints = List.empty, interceptors, stub)
}

//

class TapirStreamStubInterpreter[F[_], S, OPTIONS](
    endpoints: List[ServerEndpoint[S, F]],
    interceptors: List[Interceptor[F]],
    stub: StreamBackendStub[F, S]
) extends AbstractTapirStubInterpreter[F, S, OPTIONS](endpoints, interceptors, stub) {

  override type StubType = TapirStreamStubInterpreter[F, S, OPTIONS]

  override protected def append(sep: ServerEndpoint[S, F]): StubType =
    new TapirStreamStubInterpreter[F, S, OPTIONS](endpoints :+ sep, interceptors, stub)

  override protected def thisAsStubType: StubType = this

  /** Returns a [[StreamBackend]] which handles sent requests using Tapir's [[ServerInterpreter]]. */
  override def backend(): StreamBackend[F, S] = stub.whenAnyRequest.thenRespondF(handleRequest(_))
}

object TapirStreamStubInterpreter {
  def apply[F[_], S](stub: StreamBackendStub[F, S]): TapirStreamStubInterpreter[F, S, Unit] =
    new TapirStreamStubInterpreter[F, S, Unit](endpoints = List.empty, new CustomiseInterceptors[F, Any](_ => ()).interceptors, stub)

  def apply[F[_], S, O](options: CustomiseInterceptors[F, O], stub: StreamBackendStub[F, S]): TapirStreamStubInterpreter[F, S, O] =
    new TapirStreamStubInterpreter[F, S, O](endpoints = List.empty, options.interceptors, stub)

  def apply[F[_], S](interceptors: List[Interceptor[F]], stub: StreamBackendStub[F, S]): TapirStreamStubInterpreter[F, S, Any] =
    new TapirStreamStubInterpreter[F, S, Any](endpoints = List.empty, interceptors, stub)
}

//

class TapirWebSocketStubInterpreter[F[_], OPTIONS](
    endpoints: List[ServerEndpoint[WebSockets, F]],
    interceptors: List[Interceptor[F]],
    stub: WebSocketBackendStub[F]
) extends AbstractTapirStubInterpreter[F, WebSockets, OPTIONS](endpoints, interceptors, stub) {

  override type StubType = TapirWebSocketStubInterpreter[F, OPTIONS]

  override protected def append(sep: ServerEndpoint[WebSockets, F]): StubType =
    new TapirWebSocketStubInterpreter[F, OPTIONS](endpoints :+ sep, interceptors, stub)

  override protected def thisAsStubType: StubType = this

  /** Returns a [[WebSocketBackend]] which handles sent requests using Tapir's [[ServerInterpreter]]. */
  override def backend(): WebSocketBackend[F] = stub.whenAnyRequest.thenRespondF(handleRequest(_))
}

object TapirWebSocketStubInterpreter {
  def apply[F[_]](stub: WebSocketBackendStub[F]): TapirWebSocketStubInterpreter[F, Unit] =
    new TapirWebSocketStubInterpreter[F, Unit](endpoints = List.empty, new CustomiseInterceptors[F, Any](_ => ()).interceptors, stub)

  def apply[F[_], O](options: CustomiseInterceptors[F, O], stub: WebSocketBackendStub[F]): TapirWebSocketStubInterpreter[F, O] =
    new TapirWebSocketStubInterpreter[F, O](endpoints = List.empty, options.interceptors, stub)

  def apply[F[_]](interceptors: List[Interceptor[F]], stub: WebSocketBackendStub[F]): TapirWebSocketStubInterpreter[F, Any] =
    new TapirWebSocketStubInterpreter[F, Any](endpoints = List.empty, interceptors, stub)
}

//

class TapirWebSocketStreamStubInterpreter[F[_], S, OPTIONS](
    endpoints: List[ServerEndpoint[WebSockets with S, F]],
    interceptors: List[Interceptor[F]],
    stub: WebSocketStreamBackendStub[F, S]
) extends AbstractTapirStubInterpreter[F, WebSockets with S, OPTIONS](endpoints, interceptors, stub) {

  override type StubType = TapirWebSocketStreamStubInterpreter[F, S, OPTIONS]

  override protected def append(sep: ServerEndpoint[WebSockets with S, F]): StubType =
    new TapirWebSocketStreamStubInterpreter[F, S, OPTIONS](endpoints :+ sep, interceptors, stub)

  override protected def thisAsStubType: StubType = this

  /** Returns a [[WebSocketStreamBackend]] which handles sent requests using Tapir's [[ServerInterpreter]]. */
  override def backend(): WebSocketStreamBackend[F, S] = stub.whenAnyRequest.thenRespondF(handleRequest(_))
}

object TapirWebSocketStreamStubInterpreter {
  def apply[F[_], S](stub: WebSocketStreamBackendStub[F, S]): TapirWebSocketStreamStubInterpreter[F, S, Unit] =
    new TapirWebSocketStreamStubInterpreter[F, S, Unit](
      endpoints = List.empty,
      new CustomiseInterceptors[F, Any](_ => ()).interceptors,
      stub
    )

  def apply[F[_], S, O](
      options: CustomiseInterceptors[F, O],
      stub: WebSocketStreamBackendStub[F, S]
  ): TapirWebSocketStreamStubInterpreter[F, S, O] =
    new TapirWebSocketStreamStubInterpreter[F, S, O](endpoints = List.empty, options.interceptors, stub)

  def apply[F[_], S](
      interceptors: List[Interceptor[F]],
      stub: WebSocketStreamBackendStub[F, S]
  ): TapirWebSocketStreamStubInterpreter[F, S, Any] =
    new TapirWebSocketStreamStubInterpreter[F, S, Any](endpoints = List.empty, interceptors, stub)
}

//

abstract class AbstractTapirStubInterpreter[F[_], R, OPTIONS](
    private val endpoints: List[ServerEndpoint[R, F]],
    private val interceptors: List[Interceptor[F]],
    private val stub: AbstractBackendStub[F, R]
) { outer =>

  type StubType <: AbstractTapirStubInterpreter[F, R, OPTIONS] {
    type StubType <: outer.StubType
  }

  def whenEndpoint[I, E, O](endpoint: Endpoint[_, I, E, O, _]): TapirEndpointStub[I, E, O] = new TapirEndpointStub(endpoint)

  def whenServerEndpoint[A, U, I, E, O](serverEndpoint: ServerEndpoint.Full[A, U, I, E, O, R, F]) = new TapirServerEndpointStub(
    serverEndpoint
  )

  def whenServerEndpointRunLogic(serverEndpoint: ServerEndpoint[R, F]): StubType = append(serverEndpoint)

  def whenServerEndpointsRunLogic(serverEndpoints: List[ServerEndpoint[R, F]]): StubType =
    serverEndpoints.foldLeft(thisAsStubType) { case (stub, sep) => stub.append(sep) }

  // /** Returns `SttpBackend` which handles sent requests using a `ServerInterpreter`. */
  // def backend(): SttpBackend[F, R] =
  //   stub.whenAnyRequest.thenRespondF(req =>

  //   )

  protected def handleRequest(req: GenericRequest[_, _]): F[Response[StubBody]] =
    StubServerInterpreter(req, endpoints, interceptors)

  class TapirEndpointStub[I, E, O](ep: Endpoint[_, I, E, O, _]) {
    def thenRespond(response: O): StubType =
      append(publicEndpoint(logic = _ => _ => (Right(response): Either[E, O]).unit))

    def thenRespondError(errorResponse: E): StubType =
      append(publicEndpoint(logic = _ => _ => (Left(errorResponse): Either[E, O]).unit))

    def thenThrowException(ex: Throwable): StubType =
      append(publicEndpoint(logic = _ => _ => throw ex))

    private def publicEndpoint(logic: MonadError[F] => I => F[Either[E, O]]): ServerEndpoint[R, F] =
      ServerEndpoint.public[I, E, O, R, F](
        sttp.tapir.endpoint.in(ep.input).out(ep.output).errorOut(ep.errorOutput).asInstanceOf[Endpoint[Unit, I, E, O, R]],
        logic
      )
  }

  class TapirServerEndpointStub[A, U, I, E, O](sep: ServerEndpoint.Full[A, U, I, E, O, R, F]) {
    def thenRespond(response: O, runSecurityLogic: Boolean = true): StubType =
      if (runSecurityLogic)
        append(securedEndpoint(logic = _ => _ => _ => (Right(response): Either[E, O]).unit))
      else new TapirEndpointStub(sep.endpoint).thenRespond(response)

    def thenRespondError(errorResponse: E, runSecurityLogic: Boolean = true): StubType =
      if (runSecurityLogic)
        append(securedEndpoint(logic = _ => _ => _ => (Left(errorResponse): Either[E, O]).unit))
      else new TapirEndpointStub(sep.endpoint).thenRespondError(errorResponse)

    def thenThrowException(ex: Throwable, runSecurityLogic: Boolean = true): StubType = {
      if (runSecurityLogic)
        append(securedEndpoint(logic = _ => _ => _ => throw ex))
      else new TapirEndpointStub(sep.endpoint).thenThrowException(ex)
    }

    def thenRunLogic(): StubType = append(sep)

    private def securedEndpoint(logic: MonadError[F] => U => I => F[Either[E, O]]): ServerEndpoint[R, F] =
      ServerEndpoint[A, U, I, E, O, R, F](
        sep.endpoint,
        sep.securityLogic,
        logic
      )
  }

  protected def append(sep: ServerEndpoint[R, F]): StubType
  protected def thisAsStubType: StubType

  def backend(): GenericBackend[F, R]

  private implicit val monad: MonadError[F] = stub.monad
}
