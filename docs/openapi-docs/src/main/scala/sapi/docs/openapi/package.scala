package sapi.docs

import sapi.openapi.OpenAPI
import sapi.{Endpoint, Id}
import shapeless.HList

package object openapi {
  implicit class RichOpenAPIEndpoint[I <: HList](val e: Endpoint[Id, I, _]) extends AnyVal {
    def toOpenAPI(title: String, version: String): OpenAPI =
      EndpointToOpenAPIDocs.toOpenAPI(title, version, Seq(e))
  }

  implicit class RichOpenAPIEndpoints(val es: Seq[Endpoint[Id, _, _]]) extends AnyVal {
    def toOpenAPI(title: String, version: String): OpenAPI = EndpointToOpenAPIDocs.toOpenAPI(title, version, es)
  }
}
