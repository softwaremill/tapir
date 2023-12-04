package sttp.tapir.server.netty.internal.reactivestreams

import org.reactivestreams.Subscriber

import scala.concurrent.Future

trait PromisingSubscriber[R, A] extends Subscriber[A] {
  def future: Future[R]
}
