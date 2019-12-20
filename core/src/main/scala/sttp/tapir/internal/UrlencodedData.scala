package sttp.tapir.internal

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
    s.map {
        case (k, v) =>
          s"${URLEncoder.encode(k, charset.toString)}=${URLEncoder.encode(v, charset.toString)}"
      }
      .mkString("&")
  }
}
