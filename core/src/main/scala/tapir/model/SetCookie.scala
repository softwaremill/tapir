package tapir.model

import java.net.HttpCookie

import tapir.{Codec, CodecForMany, DecodeResult, MediaType}

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

// TODO: consider a typed model for headers - also Authorization (basic/bearer) etc.?
// TODO: make value a T & generic?
case class SetCookie(name: String,
                     value: String,
                     maxAge: Option[Long] = None,
                     domain: Option[String] = None,
                     path: Option[String] = None,
                     secure: Boolean = false,
                     httpOnly: Boolean = false) {

  def toHeaderValue: String = {
    val hc = new HttpCookie(name, value)
    maxAge.foreach(ma => hc.setMaxAge(ma))
    domain.foreach(hc.setDomain)
    path.foreach(hc.setPath)
    if (secure) hc.setSecure(true)
    if (httpOnly) hc.setHttpOnly(true)
    hc.toString
  }
}

object SetCookie {
  val HeaderName = "Set-Cookie"

  implicit val setCookieCodec: Codec[List[SetCookie], MediaType.TextPlain, String] =
    implicitly[Codec[String, MediaType.TextPlain, String]].mapDecode(parse)(cs => cs.map(_.toHeaderValue).mkString(", "))

  implicit val setCookieCodecForMany: CodecForMany[List[SetCookie], MediaType.TextPlain, String] =
    implicitly[CodecForMany[List[List[SetCookie]], MediaType.TextPlain, String]].map(_.flatten)(_.map(List(_)))

  def parse(h: String): DecodeResult[List[SetCookie]] = {
    Try(HttpCookie.parse(h).asScala.toList.map(fromHttpCookie)) match {
      case Success(v) => DecodeResult.Value(v)
      case Failure(f) => DecodeResult.Error(h, f)
    }
  }

  private def fromHttpCookie(hc: HttpCookie): SetCookie = {
    SetCookie(
      hc.getName,
      hc.getValue,
      if (hc.getMaxAge == -1) None else Some(hc.getMaxAge),
      Option(hc.getDomain),
      Option(hc.getPath),
      hc.getSecure,
      hc.isHttpOnly
    )
  }
}

case class Cookie(name: String, value: String) {
  def toHeaderValue: String = s"$name=$value"
}

object Cookie {
  val HeaderName = "Cookie"

  implicit val cookieCodec: Codec[List[Cookie], MediaType.TextPlain, String] =
    implicitly[Codec[String, MediaType.TextPlain, String]].mapDecode(parse)(cs => cs.map(_.toHeaderValue).mkString("; "))

  implicit val cookieCodecForMany: CodecForMany[List[Cookie], MediaType.TextPlain, String] =
    implicitly[CodecForMany[List[List[Cookie]], MediaType.TextPlain, String]].map(_.flatten)(List(_))

  def parse(h: String): DecodeResult[List[Cookie]] = {
    Try(h.split(";").toList.flatMap(hh => HttpCookie.parse(hh).asScala.toList)) match {
      case Success(v) => DecodeResult.Value(v.map(vv => Cookie(vv.getName, vv.getValue)))
      case Failure(f) => DecodeResult.Error(h, f)
    }
  }
}
