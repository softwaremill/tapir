package sttp.tapir.docs
import sttp.tapir.AnyEndpoint
import sttp.tapir.openapi.Server

package object openapi {

  implicit class EndpointWithDocsMetadataImplicits(endpoint: AnyEndpoint) {
    def withServers(servers: List[Server]): EndpointWithDocsMetadata = EndpointWithDocsMetadata(endpoint, servers)
    def withoutServers(): EndpointWithDocsMetadata = EndpointWithDocsMetadata(endpoint)
  }
}
