package sttp.tapir.server.netty.loom.internal.ox

import ox.*
import ox.channels.Source
import ox.channels.Actor

private[loom] class OxDispatcher()(using ox: Ox) {
  private class Runner(using Ox) {
    def runAsync(block: () => Unit): Unit =
      forkUnsupervised(block()).discard

    def transformSource[A, B](f: Ox ?=> Source[A] => Source[B], in: Source[A]): Source[B] = f(in)
  }
  private val actor = Actor.create(new Runner)

  def runAsync(block: () => Unit): Unit = actor.tell(_.runAsync(block))
  def transformSource[A, B](f: Ox ?=> Source[A] => Source[B], in: Source[A]): Source[B] = actor.ask(_.transformSource(f, in))
}
