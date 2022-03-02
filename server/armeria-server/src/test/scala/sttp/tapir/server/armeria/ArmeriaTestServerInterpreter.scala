package sttp.tapir.server.armeria

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import com.linecorp.armeria.server.Server
import sttp.capabilities.Streams
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.tests.Port

trait ArmeriaTestServerInterpreter[S <: Streams[S], F[_], OPTIONS] extends TestServerInterpreter[F, S, OPTIONS, TapirService[S, F]] {

  override def server(routes: NonEmptyList[TapirService[S, F]]): Resource[IO, Port] = {
    val bind = IO.fromCompletableFuture(
      IO {
        val serverBuilder = Server
          .builder()
          .maxRequestLength(0)
          .connectionDrainDurationMicros(0)
        routes.foldLeft(serverBuilder)((sb, route) => sb.service(route))
        val server = serverBuilder.build()
        server.start().thenApply[Server](_ => server)
      }
    )
    Resource
      .make(bind)(binding =>
        IO {
          // Ignore returned future for fast test iterations.
          // Armeria server wait for 2 seconds by default to let the boss group gracefully finish all remaining
          // tasks in the queue.
          val _ = binding.stop()
          ()
        }
      )
      .map(_.activeLocalPort())
  }
}
