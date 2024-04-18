package sttp.tapir.server.netty.loom.internal.ox

import ox.*
import ox.channels.Actor

private[loom] class OxDispatcher()(using ox: Ox) {
  private class Runner {
    def runAsync(thunk: Ox ?=> Unit, onError: Throwable => Unit): Unit =
      fork( try { thunk(using ox) } catch { case e => onError(e)} ).discard
  }
  private val actor = Actor.create(new Runner)

  def runAsync(thunk: Ox ?=> Unit)(onError: Throwable => Unit): Unit = actor.tell(_.runAsync(thunk, onError))
}
