package sttp.tapir.tests

import sttp.tapir.model.UsernamePassword
import sttp.tapir._

object Authentication {
  val in_auth_apikey_header_out_string: PublicEndpoint[String, Unit, String, Any] =
    endpoint.in("auth").in(auth.apiKey(header[String]("X-Api-Key"))).out(stringBody)

  val in_auth_apikey_query_out_string: PublicEndpoint[String, Unit, String, Any] =
    endpoint.in("auth").in(auth.apiKey(query[String]("api-key"))).out(stringBody)

  val in_auth_basic_out_string: PublicEndpoint[UsernamePassword, Unit, String, Any] =
    endpoint.in("auth").in(auth.basic[UsernamePassword]()).out(stringBody)

  val in_auth_bearer_out_string: PublicEndpoint[String, Unit, String, Any] = endpoint.in("auth").in(auth.bearer[String]()).out(stringBody)
}
