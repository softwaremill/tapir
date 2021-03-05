package sttp.tapir.server.akkahttp

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import sttp.model.{HasHeaders, Header}
import scala.collection.immutable.Seq

private[akkahttp] object AkkaModel {
  def parseHeadersOrThrow(hs: HasHeaders): Seq[HttpHeader] = hs.headers.map(parseHeaderOrThrow)

  def parseHeaderOrThrow(h: Header): HttpHeader =
    HttpHeader.parse(h.name, h.value) match {
      case ParsingResult.Ok(h, _)     => h
      case ParsingResult.Error(error) => throw new IllegalArgumentException(s"Cannot parse header (${h.name}, ${h.value}): $error")
    }
}
