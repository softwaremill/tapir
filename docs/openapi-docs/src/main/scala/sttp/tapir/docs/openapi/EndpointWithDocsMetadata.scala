package sttp.tapir.docs.openapi
import sttp.tapir.AnyEndpoint
import sttp.tapir.openapi.Server

case class EndpointWithDocsMetadata(
    endpoint: AnyEndpoint,
    servers: List[Server] = Nil
)
