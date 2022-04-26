package sttp.tapir.tests

import sttp.tapir.model.UsernamePassword
import sttp.tapir._

object Security {
  val in_security_apikey_header_out_string: Endpoint[String, Unit, Unit, String, Any] =
    endpoint.securityIn("auth").securityIn(auth.apiKey(header[String]("X-Api-Key"))).out(stringBody)

  val in_security_apikey_header_in_amount_out_string: Endpoint[String, Int, Unit, String, Any] =
    endpoint.securityIn("auth").securityIn(auth.apiKey(header[String]("X-Api-Key"))).in(query[Int]("amount")).out(stringBody)

  val in_security_apikey_query_out_string: Endpoint[String, Unit, Unit, String, Any] =
    endpoint.securityIn("auth").securityIn(auth.apiKey(query[String]("api-key"))).out(stringBody)

  val in_security_basic_out_string: Endpoint[UsernamePassword, Unit, Unit, String, Any] =
    endpoint.securityIn("auth").securityIn(auth.basic[UsernamePassword]()).out(stringBody)

  val in_security_option_basic_out_string: Endpoint[Option[UsernamePassword], Unit, Unit, String, Any] =
    endpoint.securityIn("auth").securityIn(auth.basic[Option[UsernamePassword]]()).out(stringBody)

  val in_security_option_basic_option_bearer_out_string: Endpoint[(Option[UsernamePassword], Option[String]), Unit, Unit, String, Any] =
    endpoint
      .securityIn("auth")
      .securityIn(auth.basic[Option[UsernamePassword]]())
      .securityIn(auth.bearer[Option[String]]())
      .out(stringBody)

  val in_security_bearer_out_string: Endpoint[String, Unit, Unit, String, Any] =
    endpoint.securityIn("auth").securityIn(auth.bearer[String]()).out(stringBody)
}
