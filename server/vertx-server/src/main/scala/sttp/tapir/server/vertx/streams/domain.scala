package sttp.tapir.server.vertx.streams

import io.vertx.core.Handler

import scala.collection.immutable.{Queue => SQueue}

private[vertx] trait DeferredLike[F[_], A] {

  def complete(a: A): F[Unit]

  def get: F[A]
}

private[vertx] sealed trait Queue[F[_], A] {
  def size: Int

  def enqueue(a: A): (Queue[F, A], Option[F[Unit]])

  def dequeue(dfd: DeferredLike[F, A]): (Queue[F, A], Either[DeferredLike[F, A], A])
}

private[vertx] final case class Queued[F[_], A](queue: SQueue[A]) extends Queue[F, A] {
  override def size: Int =
    queue.size

  override def enqueue(a: A): (Queue[F, A], Option[F[Unit]]) =
    (Queued[F, A](queue.enqueue(a)), None)

  override def dequeue(dfd: DeferredLike[F, A]): (Queue[F, A], Either[DeferredLike[F, A], A]) =
    queue.dequeueOption match {
      case None =>
        (Empty(dfd), Left(dfd))
      case Some((head, tail)) =>
        (Queued(tail), Right(head))
    }
}

private[vertx] final case class Empty[F[_], A](dfd: DeferredLike[F, A]) extends Queue[F, A] {
  override def size: Int =
    -1

  override def enqueue(a: A): (Queue[F, A], Option[F[Unit]]) =
    (Queued[F, A](SQueue.empty), Some(dfd.complete(a)))

  override def dequeue(x: DeferredLike[F, A]): (Queue[F, A], Either[DeferredLike[F, A], A]) =
    (this, Left(dfd))
}

private[vertx] sealed trait ActivationEvent
private[vertx] case object Pause extends ActivationEvent
private[vertx] case object Resume extends ActivationEvent

private[vertx] object ReadStreamState {
  type WrappedBuffer[C] = Either[Option[Throwable], C]
  type WrappedEvent = Option[ActivationEvent]
}

import ReadStreamState._

private[vertx] final case class ReadStreamState[F[_], C](
    buffers: Queue[F, WrappedBuffer[C]],
    activationEvents: Queue[F, WrappedEvent]
) { self =>

  def enqueue(chunk: C, maxSize: Int): (ReadStreamState[F, C], List[F[Unit]]) = {
    val (newBuffers, mbAction1) = buffers.enqueue(Right(chunk))
    val (newActivationEvents, mbAction2) = if (newBuffers.size == maxSize) {
      activationEvents.enqueue(Some(Pause))
    } else {
      (activationEvents, None)
    }
    (ReadStreamState(newBuffers, newActivationEvents), mbAction1.toList ::: mbAction2.toList)
  }

  def halt(cause: Option[Throwable]): (ReadStreamState[F, C], List[F[Unit]]) = {
    val (newBuffers, mbAction1) = buffers.enqueue(Left(cause))
    val (newActivationEvents, mbAction2) = activationEvents.enqueue(None)
    (ReadStreamState(newBuffers, newActivationEvents), mbAction1.toList ::: mbAction2.toList)
  }

  def dequeueBuffer(
      dfd: DeferredLike[F, WrappedBuffer[C]]
  ): (ReadStreamState[F, C], (Either[DeferredLike[F, WrappedBuffer[C]], WrappedBuffer[C]], Option[F[Unit]])) = {
    val (newBuffers, mbA) = buffers.dequeue(dfd)
    mbA match {
      case left @ Left(_) =>
        if (buffers.size == 0 && newBuffers.size == -1) {
          val (newActivationEvents, mbAction) = activationEvents.enqueue(Some(Resume))
          (ReadStreamState(newBuffers, newActivationEvents), (left, mbAction))
        } else {
          (ReadStreamState(newBuffers, activationEvents), (left, None))
        }
      case Right(buffer) =>
        (ReadStreamState(newBuffers, activationEvents), (Right(buffer), None))
    }
  }

  def dequeueActivationEvent(
      dfd: DeferredLike[F, WrappedEvent]
  ): (ReadStreamState[F, C], Either[DeferredLike[F, WrappedEvent], WrappedEvent]) = {
    val (newActivationEvents, mbEvent) = activationEvents.dequeue(dfd)
    (ReadStreamState(buffers, newActivationEvents), mbEvent)
  }
}

private[vertx] final case class StreamState[F[_], T](
    paused: Option[DeferredLike[F, Unit]],
    handler: Handler[T],
    errorHandler: Handler[Throwable],
    endHandler: Handler[Void]
)

private[vertx] object StreamState {
  def empty[F[_], T](promise: DeferredLike[F, Unit]) = StreamState(
    Some(promise),
    (_: T) => (),
    (_: Throwable) => (),
    (_: Void) => ()
  )
}
