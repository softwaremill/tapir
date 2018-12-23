package tapir

trait MediaType {
  def mediaType: String
}

object MediaType {
  case class Json() extends MediaType {
    override def mediaType: String = "application/json"
  }

  case class TextPlain() extends MediaType {
    override def mediaType: String = "text/plain"
  }

  case class OctetStream() extends MediaType {
    override def mediaType: String = "application/octet-stream"
  }
}
