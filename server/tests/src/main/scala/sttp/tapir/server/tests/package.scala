package sttp.tapir.server

import cats.effect.{IO, Resource}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.SttpBackend
import sttp.client3.httpclient.fs2.HttpClientFs2Backend

package object tests {
  val backendResource: Resource[IO, SttpBackend[IO, Fs2Streams[IO] with WebSockets]] = HttpClientFs2Backend.resource()
}
