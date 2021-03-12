package sttp.tapir.server.interpreter

import sttp.model.{Header, HeaderNames, MediaType}
import sttp.tapir.EndpointOutput.StatusMapping

private[interpreter] class MediaTypeNegotiator(headers: Seq[Header]) {

  val acceptedMediaTypes: Seq[(MediaType, Float)] =
    ContentNegotiation.extract(HeaderNames.Accept, headers)(MediaType.unsafeParse).sortBy {
      case (MediaType("*", "*", _), q) => (2, -q) // unbounded ranges comes last
      case (MediaType(_, "*", _), q)   => (1, -q) // bounded ranges comes next
      case (_, q)                      => (0, -q) // most specific comes first
    }

  def qValueFor(mediaType: MediaType): Float =
    acceptedMediaTypes match {
      case _ @(Nil | (MediaType("*", "*", _), _) :: Nil) => 1f // accepts all
      case mts                                           => mts collectFirst { case (mt, q) if matches(mt, mediaType) => q } getOrElse 0f
    }

  def indexOf(mediaType: MediaType): Int = acceptedMediaTypes.indexWhere { case (mt, _) => mt.noCharset == mediaType.noCharset }

  private def matches(a: MediaType, b: MediaType): Boolean =
    (a, b) match {
      case (MediaType("*", _, _), MediaType("*", _, _))                                                                           => true
      case (MediaType(mainA, "*", _), MediaType(mainB, "*", _)) if mainA.equalsIgnoreCase(mainB)                                  => true
      case (MediaType(mainA, "*", _), MediaType(mainB, _, _)) if mainA.equalsIgnoreCase(mainB)                                    => true
      case (MediaType(mainA, _, _), MediaType(mainB, "*", _)) if mainA.equalsIgnoreCase(mainB)                                    => true
      case (MediaType(mainA, subA, _), MediaType(mainB, subB, _)) if mainA.equalsIgnoreCase(mainB) && subA.equalsIgnoreCase(subB) => true
      case _                                                                                                                      => false
    }
}

private[interpreter] class CharsetNegotiator(headers: Seq[Header]) {

  val acceptedCharsets: Seq[(String, Float)] =
    ContentNegotiation.extract(HeaderNames.AcceptCharset, headers)(identity).sortBy {
      case ("*", _) => 1f // unbounded come last
      case (_, q)   => -q // others come first
    }

  def qValueFor(charset: String): Float =
    acceptedCharsets match {
      case _ @(Nil | ("*", _) :: Nil) => 1f // accepts all
      case chs                        => chs collectFirst { case (ch, q) if ch.equalsIgnoreCase(charset) => q } getOrElse 0f
    }

  def indexOf(charset: String): Int = acceptedCharsets.indexWhere { case (ch, _) => ch.equalsIgnoreCase(charset) }
}

private[interpreter] class ContentNegotiator(headers: Seq[Header]) {

  val mtn = new MediaTypeNegotiator(headers)
  val csn = new CharsetNegotiator(headers)

  def pickBest(mappings: Seq[(StatusMapping[_], MediaType)]): Option[StatusMapping[_]] = {
    def mediaIndex(mt: MediaType) = mtn.indexOf(mt)
    def charsetIndex(mt: MediaType) = mt.charset.map(csn.indexOf).getOrElse(0)

    mappings
      .map { case (m, mt) => (m, mt) -> qValueFor(mt) }
      // in case of same q value position in header is a tie breaker
      .sortBy { case ((_, mt), q) => (-q, mediaIndex(mt), charsetIndex(mt)) }
      .collectFirst { case ((m, _), q) if q > 0f => m }
  }

  private def qValueFor(mediaType: MediaType): Float =
    mediaType match {
      case m @ MediaType(_, _, Some(charset)) => math.min(csn.qValueFor(charset), mtn.qValueFor(m))
      case m @ MediaType(_, _, None)          => mtn.qValueFor(m)
    }
}

private[interpreter] object ContentNegotiation {
  private val QPattern = "q=(\\d.?\\d{1,3}?)".r

  def extract[T](name: String, headers: Seq[Header])(convert: String => T): Seq[(T, Float)] =
    headers
      .filter(_.name == name)
      .flatMap(_.value.split(",").flatMap { part =>
        part.replaceAll("\\s+", "").split(";").toList match {
          case value :: Nil => Some((convert(value), 1f))
          case value :: params =>
            val q = params collectFirst { case QPattern(q) => q }
            Some((convert(value), q.map(_.toFloat).getOrElse(1f)))
          case _ => None
        }
      })
}
