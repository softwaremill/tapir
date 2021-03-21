package sttp.tapir.server.armeria

import com.linecorp.armeria.common.{HttpHeaderNames, HttpHeaders, RequestHeaders, ResponseHeaders, MediaType => ArmeriaMediaType}
import scala.collection.JavaConverters._
import sttp.model.{Header, MediaType, StatusCode}

private[armeria] object HeaderMapping {
  def fromArmeria(headers: HttpHeaders): Seq[Header] = {
    val builder = Seq.newBuilder[Header]
    builder.sizeHint(headers.size())

    headers.forEach((key, value) => builder += Header(key.toString, value))
    builder.result()
  }

  def fromArmeria(mediaType: ArmeriaMediaType): MediaType = {
    val parameters = mediaType
      .parameters()
      .asScala
      .map { case (x, v) =>
        // The each parameter of MediaType of Armeria returns a list.
        (x, v.get(0))
      }
      .toMap
    MediaType(mediaType.`type`(), mediaType.subtype(), Option(mediaType.charset()).map(_.toString), parameters)
  }

  def toArmeria(mediaType: MediaType): ArmeriaMediaType = {
    ArmeriaMediaType.parse(mediaType.toString())
  }

  def toArmeria(headers: Seq[Header]): HttpHeaders = {
    val builder = HttpHeaders.builder()
    headers.foreach(header => builder.add(header.name, header.value))
    builder.build()
  }

  def toArmeria(headers: Seq[Header], status: StatusCode): ResponseHeaders = {
    val builder = ResponseHeaders.builder(status.code)
    headers.foreach(header => builder.add(header.name, header.value))
    builder.build()
  }
}
