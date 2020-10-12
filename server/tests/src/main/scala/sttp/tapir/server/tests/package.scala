package sttp.tapir.server

import cats.effect.{Blocker, ContextShift, IO, Resource}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend

package object tests {
  private implicit lazy val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
  val backendResource: Resource[IO, SttpBackend[IO, Fs2Streams[IO] with WebSockets]] =
    AsyncHttpClientFs2Backend.resource[IO](Blocker.liftExecutionContext(scala.concurrent.ExecutionContext.global))
}
