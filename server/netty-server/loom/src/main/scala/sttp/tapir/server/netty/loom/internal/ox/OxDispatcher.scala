package sttp.tapir.server.netty.loom.internal.ox

import ox.*
import ox.channels.Source

private[loom] class OxDispatcher(using ox: Ox) {
  def runAsync(block: () => Unit): Unit = {
    val _ = fork {
      block()
    }
  }
  def transformSource[A, B](f: Ox ?=> Source[A] => Source[B], in: Source[A]): Source[B] = f(in)
}
