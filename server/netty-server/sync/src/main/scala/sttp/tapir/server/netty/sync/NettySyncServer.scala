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
import sttp.tapir.server.netty.{NettyConfig, NettyResponse, Route}

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{CompletableFuture, ExecutionException}
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import java.util.concurrent.atomic.AtomicReference

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
    * The server runs until it's stopped using the returned binding, or until the enclosing scope ends. In both cases, the server is stopped
    * gracefully: in-flight requests are given up to [[NettyConfig.gracefulShutdownTimeout]] to complete.
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
    startInServerScope[InetSocketAddress](None) match
      case (socket, stop) =>
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
    startInServerScope(Some(new DomainSocketAddress(path.toFile))) match
      case (socket, stop) =>
        NettySyncDomainSocketBinding(socket, stop)

  /** Runs the server in a fork of the current scope. Requests are handled in forks of a nested scope, owned by the server. Hence, when the
    * current scope ends, the server is stopped (draining in-flight requests) before the request-handling forks are interrupted. Otherwise,
    * Ox would interrupt them before running any finalizers, which might call `stop()`.
    *
    * If the server's scope fails (e.g. when request handling throws a fatal error), the server is stopped, and the current scope fails.
    */
  private def startInServerScope[SA <: SocketAddress](socketOverride: Option[SA])(using Ox): (SA, () => Unit) =
    val started = new CompletableFuture[SA]()
    val stopRequested = new CompletableFuture[StopReason]()
    val stopped = new CompletableFuture[Unit]()
    forkDiscard {
      unsupervised {
        val serverStopped = new CompletableFuture[Try[Unit]]()
        forkUnsupervised {
          try serverStopped.complete(runServer(socketOverride, started, stopRequested)).discard
          catch case e: Throwable => serverStopped.completeExceptionally(e).discard
        }.discard

        // interrupted when the current scope ends
        def awaitServer(): Try[Unit] =
          try serverStopped.get()
          catch
            case _: InterruptedException =>
              stopRequested.complete(StopReason.ScopeEnded).discard
              awaitServer()

        // there's no caller of `stop()` to report the error to, and the current scope has already ended
        def logIfScopeEnded(message: String, e: Throwable): Unit =
          if stopRequested.getNow(StopReason.StopCalled) == StopReason.ScopeEnded then logger.error(message, e)

        try
          awaitServer() match
            case Success(_) => stopped.complete(()).discard
            case Failure(e) =>
              stopped.completeExceptionally(e).discard
              logIfScopeEnded("Error when stopping the server", e)
        catch
          case e: ExecutionException =>
            // start failures are reported by start(); other failures of the server's scope end the current scope
            if !started.completeExceptionally(e.getCause) then
              stopped.completeExceptionally(e.getCause).discard
              logIfScopeEnded("Server failed while stopping", e.getCause)
              throw e.getCause
      }
    }

    val socket =
      try started.get()
      catch case e: ExecutionException => throw e.getCause
    (
      socket,
      () =>
        stopRequested.complete(StopReason.StopCalled).discard
        try stopped.get().discard
        catch case e: ExecutionException => throw e.getCause
    )

  /** Runs the server in its own scope, until a stop is requested. Returns the outcome of stopping, or throws if the server's scope fails. */
  private def runServer[SA <: SocketAddress](
      socketOverride: Option[SA],
      started: CompletableFuture[SA],
      stopRequested: CompletableFuture[StopReason]
  ): Try[Unit] =
    // called once the server's scope ends, i.e. when request handling forks are done (interrupted if still running), so that their
    // callbacks can still run on the event loops
    var close: () => Unit = () => ()
    var drained: Try[Unit] = Success(())
    try
      supervised {
        val server = startUsingSocketOverride(socketOverride, inScopeRunner())
        close = server.close
        started.complete(server.socket).discard
        try stopRequested.get().discard // also interrupted if the server's scope fails
        finally drained = Try(server.drain())
      }
    catch
      case e: Throwable =>
        drained.failed.foreach(e.addSuppressed)
        Try(close()).failed.foreach(e.addSuppressed)
        throw e
    val closed = Try(close())
    drained.flatMap(_ => closed)

  private def startUsingSocketOverride[SA <: SocketAddress](
      socketOverride: Option[SA],
      inScopeRunner: InScopeRunner
  ): RunningServer[SA] =
    val endpointRoute = serverEndpoints match {
      case Vector() => Vector.empty
      case _        => Vector(NettySyncServerInterpreter(options).toRoute(serverEndpoints.toList, inScopeRunner))
    }
    val allRoutes = endpointRoute ++ otherRoutes
    val route = allRoutes match {
      case Vector()  => Route.empty
      case Vector(r) => r
      case many      => Route.combine(many)
    }
    startUsingSocketOverride(route, socketOverride, inScopeRunner)

  private def startUsingSocketOverride[SA <: SocketAddress](
      route: Route[Identity],
      socketOverride: Option[SA],
      inScopeRunner: InScopeRunner
  ): RunningServer[SA] =
    val eventLoopGroup = config.eventLoopConfig.initEventLoopGroup()

    def unsafeRunF(
        callToExecute: () => Identity[ServerResponse[NettyResponse]]
    ): (Future[ServerResponse[NettyResponse]], () => Future[Unit]) =
      val scalaPromise = Promise[ServerResponse[NettyResponse]]()

      // used to cancel the fork, possible states:
      // - Cancelled: needed if cancellation happens after the fork starts, but before it is set in the atomic reference
      // - CancellableFork: used when cancellation using interruption is enabled
      // - None: means that the fork was not set yet
      object Cancelled
      val runningFork = new AtomicReference[None.type | CancellableFork[Unit] | Cancelled.type](None)
      // #4747: we are creating forks using the concurrency scope within which the netty server is running
      // however, this is called on a Netty-managed thread, hence we need to use the inScopeRunner
      inScopeRunner.async {
        def run(): Unit =
          if runningFork.get() != Cancelled then
            try scalaPromise.success(callToExecute())
            catch
              case e: Throwable =>
                // a Promise boxes fatal errors & interruption, so a 500 is still sent
                scalaPromise.failure(e)
                if !NonFatal(e) then throw e

        if options.interruptServerLogicWhenRequestCancelled then
          val forked = forkCancellable(run())
          // we only update the state if it's not cancelled already
          runningFork.getAndAccumulate(forked, (cur, giv) => if cur != Cancelled then giv else cur) match {
            case None                     => // common "happy path" case
            case _: CancellableFork[Unit] => throw new IllegalStateException("Another fork was already set")
            case Cancelled                => forked.cancelNow() // cancellation happened before the fork was set
          }
        else forkDiscard(run())
      }

      (
        scalaPromise.future,
        () => {
          runningFork.getAndSet(Cancelled) match {
            case fork: CancellableFork[Unit] => fork.cancelNow()
            case _                           => // skip - fork not yet set
          }
          Future.unit
        }
      )

    val eventExecutor = new DefaultEventExecutor()
    val channelGroup = new DefaultChannelGroup(eventExecutor) // thread safe
    val isShuttingDown: AtomicBoolean = new AtomicBoolean(false)

    val channelIdFuture = NettyBootstrap(
      config,
      new NettyServerHandler(
        route,
        unsafeRunF,
        channelGroup,
        isShuttingDown,
        config
      ),
      eventLoopGroup,
      socketOverride
    )
    try
      channelIdFuture.sync()
      val channel = channelIdFuture.channel()
      RunningServer(
        channel.localAddress().asInstanceOf[SA],
        drain = () => drain(channel, channelGroup, isShuttingDown),
        close = () => close(Some(channel), eventLoopGroup, channelGroup, eventExecutor)
      )
    catch
      case NonFatal(startFailureCause) =>
        try close(None, eventLoopGroup, channelGroup, eventExecutor)
        catch case NonFatal(recoveryFailureCause) => startFailureCause.addSuppressed(recoveryFailureCause)
        throw startFailureCause

  private def waitForClosedChannels(
      channelGroup: ChannelGroup,
      startNanos: Long,
      gracefulShutdownTimeoutNanos: Option[Long]
  ): Unit =
    while !channelGroup.isEmpty && gracefulShutdownTimeoutNanos.exists(_ >= System.nanoTime() - startNanos) do Thread.sleep(100)
    val _ = channelGroup.close().get()

  /** Stops accepting connections, and waits for the open ones to close, up to the graceful shutdown timeout. */
  private def drain(serverChannel: Channel, channelGroup: ChannelGroup, isShuttingDown: AtomicBoolean): Unit =
    NettyShutdown.startDraining(channelGroup, isShuttingDown)
    serverChannel.close().get().discard
    waitForClosedChannels(
      channelGroup,
      startNanos = System.nanoTime(),
      gracefulShutdownTimeoutNanos = config.gracefulShutdownTimeout.map(_.toNanos)
    )

  /** Closes the server's channels, and releases its resources. */
  private def close(
      serverChannel: Option[Channel],
      eventLoopGroup: EventLoopGroup,
      channelGroup: ChannelGroup,
      eventExecutor: DefaultEventExecutor
  ): Unit =
    serverChannel.foreach(_.close().get().discard)
    channelGroup.close().get().discard
    if config.shutdownEventLoopGroupOnClose then
      // no quiet period, as request handling forks are done
      NettyShutdown.shutdownExecutor(eventLoopGroup, quietPeriodMillis = 0).get().discard
      NettyShutdown.shutdownExecutor(eventExecutor, quietPeriodMillis = 0).get().discard

  private case class RunningServer[SA](socket: SA, drain: () => Unit, close: () => Unit)

  private enum StopReason:
    case StopCalled, ScopeEnded

object NettySyncServer:
  def apply(): NettySyncServer = NettySyncServer(Vector.empty, Vector.empty, NettySyncServerOptions.default, NettyConfig.default)

  def apply(serverOptions: NettySyncServerOptions): NettySyncServer =
    NettySyncServer(Vector.empty, Vector.empty, serverOptions, NettyConfig.default)

  def apply(config: NettyConfig): NettySyncServer =
    NettySyncServer(Vector.empty, Vector.empty, NettySyncServerOptions.default, config)

  def apply(serverOptions: NettySyncServerOptions, config: NettyConfig): NettySyncServer =
    NettySyncServer(Vector.empty, Vector.empty, serverOptions, config)

case class NettySyncServerBinding(localSocket: InetSocketAddress, stop: () => Unit):
  def hostName: String = localSocket.getHostName
  def port: Int = localSocket.getPort

case class NettySyncDomainSocketBinding(localSocket: DomainSocketAddress, stop: () => Unit):
  def path: String = localSocket.path()
