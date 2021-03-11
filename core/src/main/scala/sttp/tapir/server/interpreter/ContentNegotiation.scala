package sttp.tapir.server.interpreter

import sttp.model.{Header, MediaType}

private[internal] class MediaTypeNegotiator(headers: Seq[Header]) {

  val acceptedMediaTypes: Seq[(MediaType, Float)] = ContentNegotiation.extract("Accept", s => MediaType.unsafeParse(s), headers).sortBy {
    case (MediaType("*", "*", _), q) => (2, -q) // unbounded ranges comes last
    case (MediaType(_, "*", _), q)   => (1, -q) // bounded ranges comes next
    case (_, q)                      => (0, -q) // most specific comes first
  }

  def gValueFor(mediaType: MediaType): Float =
    acceptedMediaTypes match {
      case Nil => 1f // accepts all
      case mts => mts collectFirst { case (mt, q) if mt == mediaType => q } getOrElse 0f
    }
}

private[internal] class CharsetNegotiator(headers: Seq[Header]) {

  val acceptedCharsets: Seq[(HttpCharset, Float)] = ContentNegotiation.extract("Accept-Charset", s => HttpCharset(s), headers).sortBy {
    case (HttpCharset("*"), _) => 1f // unbounded come last
    case (_, q)                => -q // others come first
  }

  def qValueFor(charset: HttpCharset): Float =
    acceptedCharsets match {
      case Nil => 1f // accepts all
      case chs => chs collectFirst { case (ch, q) if ch == charset => q } getOrElse 0f
    }
}

private[internal] case class HttpCharset(name: String)

private[internal] class ContentNegotiator(headers: Seq[Header]) {

  val mtn = new MediaTypeNegotiator(headers)
  val csn = new CharsetNegotiator(headers)

  def pickBest(alternatives: Seq[MediaType]): Option[MediaType] =
    alternatives
      .map(alt => alt -> qValueFor(alt))
      .sortBy { case (_, q) => -q }
      .collectFirst { case (alt, q) if q > 0f => alt }

  private def qValueFor(mediaType: MediaType): Float =
    mediaType match {
      case m @ MediaType(_, _, Some(charset)) => math.min(mtn.gValueFor(m), csn.qValueFor(HttpCharset(charset)))
      case m @ MediaType(_, _, None)          => mtn.gValueFor(m)
    }
}

private[internal] object ContentNegotiation {
  private val QPattern = "q=(\\d.\\d{1,3})".r

  def extract[T](name: String, convert: String => T, headers: Seq[Header]): Seq[(T, Float)] =
    headers
      .filter(_.name == name)
      .flatMap(_.value.split(",").flatMap { part =>
        part.replaceAll("\\s+", "").split(";").toList match {
          case media :: Nil => Some((convert(media), 1f))
          case media :: params =>
            val q = params collectFirst { case QPattern(q) => q }
            Some((convert(media), q.map(_.toFloat).getOrElse(1f)))
          case _ => None
        }
      })

  def apply(headers: Seq[Header]) = new ContentNegotiator(headers)
}
