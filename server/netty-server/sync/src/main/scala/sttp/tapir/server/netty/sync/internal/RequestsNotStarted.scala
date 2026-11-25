package sttp.tapir.server.netty.sync.internal

import ox.discard

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Promise

/** Requests passed to the server's scope, for which the server logic hasn't started yet. If the scope fails, they might never start; they
  * are then failed, so that they're answered with a 500, instead of delaying the shutdown.
  */
private[sync] class RequestsNotStarted:
  private val requests = ConcurrentHashMap.newKeySet[Promise[?]]()
  @volatile private var failed = false

  def add(request: Promise[?]): Unit =
    requests.add(request).discard
    // checked after adding, so that each request is failed either here, or in scopeFailed()
    if failed then fail(request)

  /** Call when the server logic starts, or when the request is cancelled. */
  def remove(request: Promise[?]): Unit = requests.remove(request).discard

  def scopeFailed(): Unit =
    failed = true
    requests.forEach(fail)

  private def fail(request: Promise[?]): Unit =
    request.tryFailure(new IllegalStateException("The server's scope failed before handling the request")).discard
