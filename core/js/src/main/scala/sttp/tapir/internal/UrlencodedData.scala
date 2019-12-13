package sttp.tapir.internal

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.Charset
import scala.scalajs.js.URIUtils

private[tapir] object UrlencodedData {
  def decode(s: String, charset: Charset): Seq[(String, String)] = {
    s.split("&")
      .toList
      .flatMap(kv =>
        kv.split("=", 2) match {
          case Array(k, v) =>
            Some((URIUtils.decodeURIComponent(k), URIUtils.decodeURIComponent(v)))
          case _ => None
        }
      )
  }

  def encode(s: Seq[(String, String)], charset: Charset): String = {
    s.map {
        case (k, v) =>
          s"${URIUtils.encodeURIComponent(k)}=${URIUtils.encodeURIComponent(v)}"
      }
      .mkString("&")
  }
}
