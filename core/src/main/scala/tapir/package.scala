import tapir.TypeMapper.{RequiredTextTypeMapper, TextTypeMapper}

import scala.annotation.implicitNotFound

package object tapir {
  def path[T: RequiredTextTypeMapper]: EndpointInput[T] =
    EndpointInput.PathCapture(implicitly[RequiredTextTypeMapper[T]], None, None, None)
  def path[T: RequiredTextTypeMapper](name: String): EndpointInput[T] =
    EndpointInput.PathCapture(implicitly[RequiredTextTypeMapper[T]], Some(name), None, None)
  implicit def stringToPath(s: String): EndpointInput[Unit] = EndpointInput.PathSegment(s)

  def query[T: TextTypeMapper](name: String): EndpointInput.Query[T] = EndpointInput.Query(name, implicitly[TextTypeMapper[T]], None, None)

  def body[T, M <: MediaType](implicit tm: TypeMapper[T, M]): EndpointIO.Body[T, M] = EndpointIO.Body(tm, None, None)
  def stringBody: EndpointIO.Body[String, MediaType.Text] = EndpointIO.Body(implicitly[TypeMapper[String, MediaType.Text]], None, None)
  def textBody[T](implicit tm: TypeMapper[T, MediaType.Text]): EndpointIO.Body[T, MediaType.Text] =
    EndpointIO.Body(tm, None, None)
  def jsonBody[T](implicit tm: TypeMapper[T, MediaType.Json]): EndpointIO.Body[T, MediaType.Json] = EndpointIO.Body(tm, None, None)

  def header[T: TextTypeMapper](name: String): EndpointIO.Header[T] = EndpointIO.Header(name, implicitly[TextTypeMapper[T]], None, None)

  case class InvalidOutput(reason: DecodeResult[Nothing], cause: Option[Throwable]) extends Exception(cause.orNull)
//  case class InvalidInput(input: EndpointInput.Single[_], reason: TypeMapper.Result[Nothing], cause: Option[Throwable])
//      extends Exception(cause.orNull)

  val endpoint: Endpoint[Unit, Unit, Unit] =
    Endpoint[Unit, Unit, Unit](
      Method.GET,
      EndpointInput.Multiple(Vector.empty),
      EndpointIO.Multiple(Vector.empty),
      EndpointIO.Multiple(Vector.empty),
      None,
      None,
      None,
      Vector.empty
    )
}
