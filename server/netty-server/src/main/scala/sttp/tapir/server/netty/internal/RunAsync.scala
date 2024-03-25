package sttp.tapir.server.netty.internal

import scala.concurrent.Future

trait RunAsync[F[_]] {
  def apply(f: => F[Unit]): Unit
}
object RunAsync {
  type Id[A] = A
  
  final val Id: RunAsync[Id] = new RunAsync[Id] {
    override def apply(f: => Id[Unit]): Unit = f
  }
  
  final val Future: RunAsync[Future] = new RunAsync[Future] {
    override def apply(f: => Future[Unit]): Unit = 
      f: Unit
  }
}
