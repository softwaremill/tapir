package sttp.tapir.server.netty.sync

import io.netty.channel.group.{ChannelGroup, DefaultChannelGroup}
import io.netty.channel.unix.DomainSocketAddress
import io.netty.channel.{Channel, EventLoopGroup}
import io.netty.util.concurrent.DefaultEventExecutor
import org.slf4j.LoggerFactory
import ox.*
import sttp.capabilities.WebSockets
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler, NettyShutdown}
import sttp.tapir.server.netty.sync.internal.RequestsNotStarted
import sttp.tapir.server.netty.{NettyConfig, NettyResponse, Route}

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{CompletableFuture, ExecutionException}
import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Success, Try}

case class NettySyncServer(
    serverEndpoints: Vector[ServerEndpoint[OxStreams & WebSockets, Identity]],
    otherRoutes: Vector[IdRoute],
    options: NettySyncServerOptions,
    config: NettyConfig
):
  private val logger = LoggerFactory.getLogger(getClass.getName)

  def addEndpoint(se: ServerEndpoint[OxStreams & WebSockets, Identity]): NettySyncServer = addEndpoints(List(se))
  def addEndpoints(ses: List[ServerEndpoint[OxStreams & WebSockets, Identity]]): NettySyncServer =
    copy(serverEndpoints = serverEndpoints ++ ses)

  /** Adds a custom route to the server. When a request is received, it is first processed by routes generated from the defined endpoints
    * (see [[addEndpoint]] and [[addEndpoints]] for the primary methods of defining server behavior). If none of these endpoints match the
    * request, and the [[RejectHandler]] is configured to allow fallback handling (see below), the request will then be processed by the
    * custom routes added using this method, in the order they were added.
    *
    * By default, the [[NettySyncServerOptions]] are configured to return a `404` response when no endpoints match a request. This behavior
    * is controlled by the [[RejectHandler]]. If you intend to handle unmatched requests using custom routes, ensure that the
    * [[RejectHandler]] is configured appropriately to allow such fallback handling.
    */
  def addRoute(r: IdRoute): NettySyncServer = copy(otherRoutes = otherRoutes :+ r)

  /** Adds custom routes to the server. When a request is received, it is first processed by routes generated from the defined endpoints
    * (see [[addEndpoint]] and [[addEndpoints]] for the primary methods of defining server behavior). If none of these endpoints match the
    * request, and the [[RejectHandler]] is configured to allow fallback handling (see below), the request will then be processed by the
    * custom routes added using this method, in the order they were added.
    *
    * By default, the [[NettySyncServerOptions]] are configured to return a `404` response when no endpoints match a request. This behavior
    * is controlled by the [[RejectHandler]]. If you intend to handle unmatched requests using custom routes, ensure that the
    * [[RejectHandler]] is configured appropriately to allow such fallback handling.
    */
  def addRoutes(r: Iterable[IdRoute]): NettySyncServer = copy(otherRoutes = otherRoutes ++ r)

  def options(o: NettySyncServerOptions): NettySyncServer = copy(options = o)
  def config(c: NettyConfig): NettySyncServer = copy(config = c)
  def modifyConfig(f: NettyConfig => NettyConfig): NettySyncServer = config(f(config))

  def host(hostname: String): NettySyncServer = modifyConfig(_.host(hostname))

  def port(p: Int): NettySyncServer = modifyConfig(_.port(p))

  /** Use only if you need to manage server lifecycle or concurrency scope manually. Otherwise, see [[startAndWait]].
    *
    * The server runs until it's stopped using the returned binding, or until the enclosing scope ends (then, `stop()` doesn't need to be
    * called). In both cases, the server is stopped gracefully: in-flight requests are given up to
    * [[NettyConfig.gracefulShutdownTimeout]] to complete; the scope ends once that's done.
    *
    * If request handling fails with a fatal error, and [[NettySyncServerOptions.interruptServerLogicWhenRequestCancelled]] is `false`, the
    * server is stopped, and the enclosing scope fails with that error.
    *
    * @example
    *   {{{
    *   import ox.*
    *
    *   supervised {
    *     val serverBinding = server.start()
    *     println(s"Tapir is running on port ${serverBinding.port})
    *     never
    *   }
    *   }}}
    * @return
    *   server binding, to be used to control stopping of the server or obtaining metadata like port.
    */
  def start()(using Ox): NettySyncServerBinding =
    val (socket, stop) = startServerFork[InetSocketAddress](None)
    NettySyncServerBinding(socket, stop).tap: binding =>
      logger.info(s"Tapir Netty server started on ${binding.hostName}:${binding.port}")

  /** Starts the server and blocks current virtual thread. Ensures graceful shutdown if the running server gets interrupted. Use [[start]]
    * if you need to manually control concurrency scope or server lifecycle.
    */
  def startAndWait(): Unit =
    supervised {
      start().discard
      never
    }

  /** As [[start]], but listens on the given Unix domain socket. */
  def startUsingDomainSocket(path: Path)(using Ox): NettySyncDomainSocketBinding =
    val (socket, stop) = startServerFork(Some(new DomainSocketAddress(path.toFile)))
    NettySyncDomainSocketBinding(socket, stop)

  /** Starts the server, which runs until it's stopped, or until the current scope ends. If the server fails, the current scope fails.
    *
    * @return
    *   the bound address, and a function which stops the server.
    */
  private def startServerFork[SA <: SocketAddress](socketOverride: Option[SA])(using Ox): (SA, () => Unit) =
    val started = new CompletableFuture[SA]()
    val stopRequested = new CompletableFuture[Unit]()
    val serverOutcome = new CompletableFuture[ServerOutcome]()

    // waits for the server to stop; interrupted when the current scope ends, and then requests the stop
    forkDiscard {
      unsupervised {
        // the server runs on a thread which isn't interrupted when the current scope ends, and handles requests in forks of its own
        // scope, so that they can complete while draining (otherwise, Ox would interrupt them before running any finalizers)
        forkUnsupervised {
          val outcome =
            try runServer(socketOverride, started, stopRequested)
            catch case e: Throwable => ServerOutcome.Failed(e)
          serverOutcome.complete(outcome).discard
        }.discard

        val (outcome, scopeEnded) =
          try (serverOutcome.get(), false)
          catch
            case _: InterruptedException => // the current scope ended
              stopRequested.complete(()).discard
              // join() ignores interrupts; Ox interrupts a fork only once
              (serverOutcome.join(), true)

        outcome match
          case ServerOutcome.Stopped       => ()
          // if the scope ended, stop() might never be called, so the error is logged; otherwise, stop() throws it
          case ServerOutcome.StopFailed(e) => if scopeEnded then logger.error("Error when stopping the server", e)
          case ServerOutcome.Failed(e)     =>
            // if the server didn't start, the error is thrown from start()
            if !started.completeExceptionally(e) then
              // also logging, as the error is lost if the current scope has already ended
              logger.error("The server failed", e)
              throw e
      }
    }

    val socket =
      try started.get()
      catch case e: ExecutionException => throw e.getCause
    val stop = () =>
      stopRequested.complete(()).discard
      // a new exception on each call: `stop()` might be called again, e.g. in a finalizer of a scope which already failed with the
      // exception thrown previously, and Ox can't add an exception as suppressed to itself
      serverOutcome.get() match
        case ServerOutcome.Stopped       => ()
        case ServerOutcome.StopFailed(e) => throw new IllegalStateException("Stopping the server failed", e)
        case ServerOutcome.Failed(e)     => throw new IllegalStateException("The server failed", e)
    (socket, stop)

  /** Binds the server, and runs it in its own scope until a stop is requested. */
  private def runServer[SA <: SocketAddress](
      socketOverride: Option[SA],
      started: CompletableFuture[SA],
      stopRequested: CompletableFuture[Unit]
  ): ServerOutcome =
    val eventLoopGroup = config.eventLoopConfig.initEventLoopGroup()
    val eventExecutor = new DefaultEventExecutor()
    val channelGroup = new DefaultChannelGroup(eventExecutor) // thread safe
    val isShuttingDown = new AtomicBoolean(false)
    val requestsNotStarted = new RequestsNotStarted
    // set in the server's scope, read once it ends (its forks are then joined, so the writes are visible); vars, as the scope might fail
    var serverChannel: Option[Channel] = None
    var drained: Try[Unit] = Success(())

    val scopeResult =
      try
        Right(supervised {
          val runner = inScopeRunner()
          val serverRoute = route(runner)
          val channel = NettyBootstrap(
            config,
            new NettyServerHandler(serverRoute, unsafeRunF(runner, requestsNotStarted), channelGroup, isShuttingDown, config),
            eventLoopGroup,
            socketOverride
          ).sync().channel()
          serverChannel = Some(channel)
          started.complete(channel.localAddress().asInstanceOf[SA]).discard

          // Ox interrupts this thread once, when the server's scope fails: while waiting for the stop, or while draining. The scope then
          // fails with the error which caused this, so the interruption isn't rethrown.
          try stopRequested.get().discard
          catch case _: InterruptedException => requestsNotStarted.scopeFailed()
          val drain = NettyShutdown.drain(channel, channelGroup, eventExecutor, isShuttingDown, config.gracefulShutdownTimeout)
          def awaitDrain(): Try[Unit] = Try(drain.get().discard)
          drained =
            try awaitDrain()
            catch
              case _: InterruptedException =>
                requestsNotStarted.scopeFailed()
                awaitDrain()
        })
      catch case e: Throwable => Left(e)

    // closing once request-handling forks are done (interrupted if still running), so that their callbacks can still run on the event loops
    val closed = Try(closeServer(serverChannel, eventLoopGroup, channelGroup, eventExecutor))
    scopeResult match
      case Left(e) =>
        drained.failed.foreach(e.addSuppressed)
        closed.failed.foreach(e.addSuppressed)
        ServerOutcome.Failed(e)
      case Right(_) => drained.flatMap(_ => closed).fold(ServerOutcome.StopFailed(_), _ => ServerOutcome.Stopped)

  private def route(inScopeRunner: InScopeRunner): Route[Identity] =
    val endpointRoute = serverEndpoints match {
      case Vector() => Vector.empty
      case _        => Vector(NettySyncServerInterpreter(options).toRoute(serverEndpoints.toList, inScopeRunner))
    }
    endpointRoute ++ otherRoutes match {
      case Vector()  => Route.empty
      case Vector(r) => r
      case many      => Route.combine(many)
    }

  private def unsafeRunF(inScopeRunner: InScopeRunner, requestsNotStarted: RequestsNotStarted)(
      callToExecute: () => Identity[ServerResponse[NettyResponse]]
  ): (Future[ServerResponse[NettyResponse]], () => Future[Unit]) =
    val scalaPromise = Promise[ServerResponse[NettyResponse]]()

    val cancelled = new AtomicBoolean(false)
    // completed only if cancellation using interruption is enabled
    val cancellableFork = new CompletableFuture[CancellableFork[Unit]]()
    requestsNotStarted.add(scalaPromise)
    // #4747: we are creating forks using the concurrency scope within which the netty server is running
    // however, this is called on a Netty-managed thread, hence we need to use the inScopeRunner
    inScopeRunner.async {
      def run(): Unit =
        requestsNotStarted.remove(scalaPromise)
        if !cancelled.get() then
          try scalaPromise.trySuccess(callToExecute()).discard
          catch
            case e: Throwable =>
              // a Promise boxes fatal errors & interruption, so a 500 is still sent
              scalaPromise.tryFailure(e).discard
              if !NonFatal(e) then throw e

      if options.interruptServerLogicWhenRequestCancelled then cancellableFork.complete(forkCancellable(run())).discard
      else forkDiscard(run())
    }

    (
      scalaPromise.future,
      () => {
        requestsNotStarted.remove(scalaPromise)
        cancelled.set(true)
        // if the fork isn't created yet, cancels it once it is
        cancellableFork.thenAccept(_.cancelNow()).discard
        Future.unit
      }
    )

  /** Closes the server's channels, and releases its resources. */
  private def closeServer(
      serverChannel: Option[Channel],
      eventLoopGroup: EventLoopGroup,
      channelGroup: ChannelGroup,
      eventExecutor: DefaultEventExecutor
  ): Unit =
    serverChannel.foreach(_.close().get().discard)
    channelGroup.close().get().discard
    val eventLoopGroupToShutDown = Some(eventLoopGroup).filter(_ => config.shutdownEventLoopGroupOnClose)
    // no quiet period, as request handling forks are done
    NettyShutdown.shutdownExecutors(eventLoopGroupToShutDown, eventExecutor, eventLoopGroupQuietPeriod = Duration.Zero).get().discard

object NettySyncServer:
  def apply(): NettySyncServer = NettySyncServer(Vector.empty, Vector.empty, NettySyncServerOptions.default, NettyConfig.default)

  def apply(serverOptions: NettySyncServerOptions): NettySyncServer =
    NettySyncServer(Vector.empty, Vector.empty, serverOptions, NettyConfig.default)

  def apply(config: NettyConfig): NettySyncServer =
    NettySyncServer(Vector.empty, Vector.empty, NettySyncServerOptions.default, config)

  def apply(serverOptions: NettySyncServerOptions, config: NettyConfig): NettySyncServer =
    NettySyncServer(Vector.empty, Vector.empty, serverOptions, config)

private enum ServerOutcome:
  case Stopped
  // draining or closing failed
  case StopFailed(e: Throwable)
  // the server's scope failed, including when binding
  case Failed(e: Throwable)

/** @param stop
  *   Stops the server gracefully, and waits until it's stopped. Can be called more than once. Errors are thrown wrapped in an
  *   `IllegalStateException`.
  */
case class NettySyncServerBinding(localSocket: InetSocketAddress, stop: () => Unit):
  def hostName: String = localSocket.getHostName
  def port: Int = localSocket.getPort

/** @param stop
  *   As in [[NettySyncServerBinding]].
  */
case class NettySyncDomainSocketBinding(localSocket: DomainSocketAddress, stop: () => Unit):
  def path: String = localSocket.path()
