package sttp.tapir.server.netty.loom.internal.ox

import ox.*
import ox.channels.Actor

private[loom] class OxDispatcher()(using ox: Ox) {
  private class Runner {
    def runAsync(thunk: Ox ?=> Unit): Unit =
      fork(thunk(using ox)).discard
  }
  private val actor = Actor.create(new Runner)

  def runAsync(thunk: Ox ?=> Unit): Unit = actor.tell(_.runAsync(thunk))
}
