package sttp.tapir.annotations

import java.nio.charset.StandardCharsets

import sttp.tapir.CodecFormat
import sttp.tapir.RawBodyType
import sttp.tapir.RawBodyType.StringBody

import scala.annotation._

sealed trait EndpointInputAnnotation extends StaticAnnotation

sealed trait EndpointOutputAnnotation extends StaticAnnotation

class path extends EndpointInputAnnotation

class query(val name: String = "") extends EndpointInputAnnotation

class params extends EndpointInputAnnotation

class header(val name: String = "") extends EndpointInputAnnotation with EndpointOutputAnnotation

class headers extends EndpointInputAnnotation with EndpointOutputAnnotation

class cookie(val name: String = "") extends EndpointInputAnnotation

class cookies extends EndpointInputAnnotation with EndpointOutputAnnotation

class setCookie(val name: String = "") extends EndpointOutputAnnotation

class setCookies extends EndpointOutputAnnotation

class statusCode extends EndpointOutputAnnotation

class body[R, CF <: CodecFormat](val bodyType: RawBodyType[R], val cf: CF)
  extends EndpointInputAnnotation
  with EndpointOutputAnnotation

class jsonbody extends body(StringBody(StandardCharsets.UTF_8), CodecFormat.Json())

class xmlbody extends body(StringBody(StandardCharsets.UTF_8), CodecFormat.Xml())

class apikey extends EndpointInputAnnotation

class basic extends EndpointInputAnnotation

class bearer extends EndpointInputAnnotation

class endpointInput(val path: String = "") extends EndpointInputAnnotation
