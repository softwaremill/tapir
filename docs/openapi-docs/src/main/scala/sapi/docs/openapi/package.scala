package sapi.docs

import sapi.openapi.OpenAPI
import sapi.{Endpoint, Id}
import shapeless.HList

package object openapi {
  implicit class RichOpenAPIEndpoint[I <: HList, O <: HList, OE <: HList](val e: Endpoint[I, O, OE]) extends AnyVal {
    def toOpenAPI(title: String, version: String): OpenAPI =
      EndpointToOpenAPIDocs.toOpenAPI(title, version, Seq(e))
  }

  implicit class RichOpenAPIEndpoints[I <: HList, O <: HList, OE <: HList](val es: Seq[Endpoint[I, O, OE]]) extends AnyVal {
    def toOpenAPI(title: String, version: String): OpenAPI = EndpointToOpenAPIDocs.toOpenAPI(title, version, es)
  }
}
