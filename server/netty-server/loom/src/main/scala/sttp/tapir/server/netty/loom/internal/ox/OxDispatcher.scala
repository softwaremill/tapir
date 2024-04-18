package sttp.tapir.server.netty.loom.internal.ox

import ox.*
import ox.channels.Actor

/** A dispatcher that can start arbitrary forks. Useful when one needs to start an asynchronous task from a thread outside of an Ox scope.
  * Normally Ox doesn't allow to start forks from other threads, for example in callbacks of external libraries. If you create an
  * OxDispatcher inside a scope and pass it for potential handling on another thread, that thread can call
  * {{{
  *   dispatcher.runAsync {
  *     // code to be executed in a fork
  *   } { throwable =>
  *     // error handling if the fork fails with an exception, this will be run on the Ox virtual thread as well
  *   }
  * }}}
  * WARNING! Dispatchers should only be used in special cases, where the proper structure of concurrency scopes cannot be preserved. One
  * such example is integration with callback-based systems like Netty, which runs handler methods on its event loop thread.
  * @param ox
  *   concurrency scope where a fork will be run, using a nested scope to isolate failures.
  */
private[loom] class OxDispatcher()(using ox: Ox):
  private class Runner:
    def runAsync(thunk: Ox ?=> Unit, onError: Throwable => Unit): Unit =
      fork {
        try supervised(thunk)
        catch case e => onError(e)
      }.discard

  private val actor = Actor.create(new Runner)

  def runAsync(thunk: Ox ?=> Unit)(onError: Throwable => Unit): Unit = actor.tell(_.runAsync(thunk, onError))
