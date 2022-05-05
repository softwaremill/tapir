package sttp.tapir.internal

import sttp.model.internal.Rfc3986

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.Charset

private[tapir] object UrlencodedData {
  def decode(s: String, charset: Charset): Seq[(String, String)] = {
    s.split("&")
      .toList
      .flatMap(kv =>
        kv.split("=", 2) match {
          case Array(k, v) =>
            Some((URLDecoder.decode(k, charset.toString), URLDecoder.decode(v, charset.toString)))
          case _ => None
        }
      )
  }

  def encode(s: Seq[(String, String)], charset: Charset): String = {
    s.map { case (k, v) =>
      s"${URLEncoder.encode(k, charset.toString)}=${URLEncoder.encode(v, charset.toString)}"
    }.mkString("&")
  }

  def encode(s: String): String = {
    URLEncoder.encode(s, "UTF-8")
  }

  def encodePathSegment(s: String): String = {
    Rfc3986.encode(Rfc3986.PathSegment)(s)
  }
}
