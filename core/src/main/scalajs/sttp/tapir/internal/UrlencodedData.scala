package sttp.tapir.internal

import sttp.model.internal.Rfc3986

import java.nio.charset.Charset
import scala.scalajs.js.URIUtils

private[tapir] object UrlencodedData {
  def decode(s: String, charset: Charset): Seq[(String, String)] = {
    def decodeSpace(in: String): String = in.replace('+', ' ')
    s.split("&")
      .toList
      .flatMap(kv =>
        kv.split("=", 2) match {
          case Array(k, v) =>
            Some((URIUtils.decodeURIComponent(decodeSpace(k)), URIUtils.decodeURIComponent(decodeSpace(v))))
          case _ => None
        }
      )
  }

  def encode(s: Seq[(String, String)], charset: Charset): String = {
    def encodeSpace(in: String): String = in.replace("%20", "+")

    s.map { case (k, v) =>
      s"${encodeSpace(URIUtils.encodeURIComponent(k))}=${encodeSpace(URIUtils.encodeURIComponent(v))}"
    }.mkString("&")
  }

  def encode(s: String): String = {
    URIUtils.encodeURIComponent(s)
  }

  def encodePathSegment(s: String): String = {
    Rfc3986.encode(Rfc3986.PathSegment)(s)
  }
}
