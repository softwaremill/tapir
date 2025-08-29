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
import sttp.tapir.server.netty.internal.{NettyBootstrap, NettyServerHandler}
import sttp.tapir.server.netty.{NettyConfig, NettyResponse, Route}

import java.net.{InetSocketAddress, SocketAddress}
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal
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
    * @example
    *   {{{
    *   import ox.*
    *
    *   supervised {
    *     val serverBinding = useInScope(server.start())(_.stop())
    *     println(s"Tapir is running on port ${serverBinding.port})
    *     never
    *   }
    *   }}}
    * @return
    *   server binding, to be used to control stopping of the server or obtaining metadata like port.
    */
  def start()(using Ox): NettySyncServerBinding =
    startUsingSocketOverride[InetSocketAddress](None, inScopeRunner()) match
      case (socket, stop) =>
        NettySyncServerBinding(socket, stop).tap: binding =>
          logger.info(s"Tapir Netty server started on ${binding.hostName}:${binding.port}")

  /** Starts the server and blocks current virtual thread. Ensures graceful shutdown if the running server gets interrupted. Use [[start]]
    * if you need to manually control concurrency scope or server lifecycle.
    */
  def startAndWait(): Unit =
    supervised {
      useInScope(start())(_.stop()).discard
      never
    }

  def startUsingDomainSocket(path: Path)(using Ox): NettySyncDomainSocketBinding =
    startUsingSocketOverride(Some(new DomainSocketAddress(path.toFile)), inScopeRunner()) match
      case (socket, stop) =>
        NettySyncDomainSocketBinding(socket, stop)

  private def startUsingSocketOverride[SA <: SocketAddress](
      socketOverride: Option[SA],
      inScopeRunner: InScopeRunner
  ): (SA, () => Unit) =
    val endpointRoute = NettySyncServerInterpreter(options).toRoute(serverEndpoints.toList, inScopeRunner)
    val route = Route.combine(endpointRoute +: otherRoutes)
    startUsingSocketOverride(route, socketOverride, inScopeRunner)

  private def startUsingSocketOverride[SA <: SocketAddress](
      route: Route[Identity],
      socketOverride: Option[SA],
      inScopeRunner: InScopeRunner
  ): (SA, () => Unit) =
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
            catch case NonFatal(e) => scalaPromise.failure(e)

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
      val channelId = channelIdFuture.channel()
      (
        channelId.localAddress().asInstanceOf[SA],
        () => stop(channelId, eventLoopGroup, channelGroup, eventExecutor, isShuttingDown, config.gracefulShutdownTimeout)
      )
    catch
      case NonFatal(startFailureCause) =>
        try stopRecovering(eventLoopGroup, channelGroup, eventExecutor, isShuttingDown, config.gracefulShutdownTimeout)
        catch case NonFatal(recoveryFailureCause) => startFailureCause.addSuppressed(recoveryFailureCause)
        throw startFailureCause

  private def waitForClosedChannels(
      channelGroup: ChannelGroup,
      startNanos: Long,
      gracefulShutdownTimeoutNanos: Option[Long]
  ): Unit =
    while !channelGroup.isEmpty && gracefulShutdownTimeoutNanos.exists(_ >= System.nanoTime() - startNanos) do Thread.sleep(100)
    val _ = channelGroup.close().get()

  private def stop(
      ch: Channel,
      eventLoopGroup: EventLoopGroup,
      channelGroup: ChannelGroup,
      eventExecutor: DefaultEventExecutor,
      isShuttingDown: AtomicBoolean,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Unit =
    isShuttingDown.set(true)
    waitForClosedChannels(
      channelGroup,
      startNanos = System.nanoTime(),
      gracefulShutdownTimeoutNanos = gracefulShutdownTimeout.map(_.toNanos)
    )
    ch.close().get()
    if config.shutdownEventLoopGroupOnClose then
      val _ = eventLoopGroup.shutdownGracefully().get()
      val _ = eventExecutor.shutdownGracefully().get()

  private def stopRecovering(
      eventLoopGroup: EventLoopGroup,
      channelGroup: ChannelGroup,
      eventExecutor: DefaultEventExecutor,
      isShuttingDown: AtomicBoolean,
      gracefulShutdownTimeout: Option[FiniteDuration]
  ): Unit =
    isShuttingDown.set(true)
    waitForClosedChannels(
      channelGroup,
      startNanos = System.nanoTime(),
      gracefulShutdownTimeoutNanos = gracefulShutdownTimeout.map(_.toNanos)
    )
    if config.shutdownEventLoopGroupOnClose then
      val _ = eventLoopGroup.shutdownGracefully().get()
      val _ = eventExecutor.shutdownGracefully().get()

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
