package sttp.tapir.server

import cats.effect.{IO, Resource}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import sttp.client3.{PartialRequest, SttpBackend, asStringAlways, basicRequest}
import sttp.model.Header
import sttp.monad.MonadError

package object tests {
  val backendResource: Resource[IO, SttpBackend[IO, Fs2Streams[IO] with WebSockets]] = HttpClientFs2Backend.resource()
  val basicStringRequest: PartialRequest[String, Any] = basicRequest.response(asStringAlways)
  def pureResult[F[_]: MonadError, T](t: T): F[T] = implicitly[MonadError[F]].unit(t)
  def suspendResult[F[_]: MonadError, T](t: => T): F[T] = implicitly[MonadError[F]].eval(t)

  val stripEnclosingDoubleQuotesFromString: String => String = string =>
    if (string.startsWith("\"") && string.endsWith("\"")) string.stripPrefix("\"").stripSuffix("\"")
    else string

  val stripEnclosingDoubleQuotesFromHeader: Header => Header = header =>
    Header(header.name, stripEnclosingDoubleQuotesFromString(header.value))

  val reduceRepetitiveDoubleQuotes: String => String = string =>
    "(\")+".r.replaceAllIn(string, "\"")

}
