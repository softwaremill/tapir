import tapir.TypeMapper.{RequiredTextTypeMapper, TextTypeMapper}

import scala.annotation.implicitNotFound

package object tapir {
  /*
    Goals:
    - user-friendly types (also in idea); as simple as possible to generate the client, server & docs
    - Swagger-first
    - reasonably type-safe: only as much as needed to gen a server/client/docs, no more
    - programmer friendly (ctrl-space)
   */

  /*
  Akka http directives:
  - authenticate basic/oauth, authorize (fn)
  - cache responses
  - complete (with)
  - decompress request with
  - add/remove cookie
  - extract headers
  - extract body: entity, form field; save to file
  - method matchers
  - complete with file/directory
  - transform request or response (fn)
  - extract parameters
  - match path (extract suffix, ignore trailing slash)
  - redirects
   */

  // define model using case classes
  // capture path components and their mapping to parameters
  // capture query, body, cookie, header parameters w/ mappings
  // read a yaml to get the model / auto-generate the model from a yaml ?
  //   -> only generation possible, due to type-safety
  //   -> the scala model is richer, as it has all the types + case classes
  // server: generate an http4s/akka endpoint matcher
  // client: generate an sttp request definition

  // separate logic from endpoint definition & documentation

  // provide as much or as little detail as needed: optional query param/endpoint desc, samples
  // reasonably type-safe

  // https://github.com/felixbr/swagger-blocks-scala
  // https://typelevel.org/blog/2018/06/15/typedapi.html (https://github.com/pheymann/typedapi)
  // http://fintrospect.io/defining-routes
  // https://github.com/http4s/rho
  // https://github.com/TinkoffCreditSystems/typed-schema

  // what to capture: path, query parameters, body, headers, default response body, error response body

  // streaming?

  // type: string, format: base64, binary, email, ... - use tagged string types ?
  // type: object                                     - implicit EndpointInputType values
  // form fields, multipart uploads, ...

  // extend the path for an endpoint?
  //
  // types, that you are not afraid to write down
  // Human comprehensible types

  // name: descriptions? recipies?

  //

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
