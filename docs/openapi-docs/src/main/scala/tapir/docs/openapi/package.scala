package tapir.docs

import tapir.Endpoint
import tapir.openapi.OpenAPI

package object openapi {
  implicit class RichOpenAPIEndpoint[I, E, O](val e: Endpoint[I, E, O]) extends AnyVal {
    def toOpenAPI(title: String, version: String): OpenAPI =
      EndpointToOpenAPIDocs.toOpenAPI(title, version, Seq(e))
  }

  implicit class RichOpenAPIEndpoints[I, E, O](val es: Seq[Endpoint[I, E, O]]) extends AnyVal {
    def toOpenAPI(title: String, version: String): OpenAPI = EndpointToOpenAPIDocs.toOpenAPI(title, version, es)
  }
}
