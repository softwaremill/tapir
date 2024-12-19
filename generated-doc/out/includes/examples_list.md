## Hello, World!

* [A demo of Tapir's capabilities](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/booksExample.scala) <span class="example-tag example-json">circe</span> <span class="example-tag example-client">sttp3</span> <span class="example-tag example-docs">Swagger UI</span> <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Pekko HTTP</span>
* [A demo of Tapir's capabilities](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/booksPicklerExample.scala) <span class="example-tag example-json">Pickler</span> <span class="example-tag example-client">sttp3</span> <span class="example-tag example-docs">Swagger UI</span> <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Netty</span>
* [Exposing an endpoint using the Armeria server](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/helloWorldArmeriaServer.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Armeria</span>
* [Exposing an endpoint using the Netty server (Direct-style variant)](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/helloWorldNettySyncServer.scala) <span class="example-tag example-effects">Direct</span> <span class="example-tag example-server">Netty</span>
* [Exposing an endpoint using the Netty server (Future variant)](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/helloWorldNettyFutureServer.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Netty</span>
* [Exposing an endpoint using the Netty server (cats-effect variant)](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/HelloWorldNettyCatsServer.scala) <span class="example-tag example-effects">cats-effect</span> <span class="example-tag example-server">Netty</span>
* [Exposing an endpoint using the Pekko HTTP server](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/helloWorldPekkoServer.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Pekko HTTP</span>
* [Exposing an endpoint using the ZIO HTTP server](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/HelloWorldZioHttpServer.scala) <span class="example-tag example-effects">ZIO</span> <span class="example-tag example-json">ZIO JSON</span> <span class="example-tag example-server">ZIO HTTP</span>
* [Exposing an endpoint using the ZIO HTTP server](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/ZioExampleZioHttpServer.scala) <span class="example-tag example-docs">Swagger UI</span> <span class="example-tag example-effects">ZIO</span> <span class="example-tag example-json">circe</span> <span class="example-tag example-server">zio-http</span>
* [Exposing an endpoint using the built-in JDK HTTP server](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/helloWorldJdkHttpServer.scala) <span class="example-tag example-effects">Direct</span> <span class="example-tag example-server">JDK Http</span>
* [Exposing an endpoint using the http4s server](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/HelloWorldHttp4sServer.scala) <span class="example-tag example-effects">cats-effect</span> <span class="example-tag example-server">http4s</span>
* [Exposing an endpoint using the http4s server](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/ZioExampleHttp4sServer.scala) <span class="example-tag example-docs">Swagger UI</span> <span class="example-tag example-effects">ZIO</span> <span class="example-tag example-json">circe</span> <span class="example-tag example-server">http4s</span>
* [Exposing an endpoint, defined with ZIO and depending on services in the environment, using the http4s server](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/ZioEnvExampleHttp4sServer.scala) <span class="example-tag example-docs">Swagger UI</span> <span class="example-tag example-effects">ZIO</span> <span class="example-tag example-json">circe</span> <span class="example-tag example-server">http4s</span>
* [Extending a base endpoint (which has the security logic provided), with server logic](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/ZioPartialServerLogicHttp4s.scala) <span class="example-tag example-effects">ZIO</span> <span class="example-tag example-server">http4s</span>

## Client interpreter

* [Interpreting an endpoint as an http4s client](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/client/Http4sClientExample.scala) <span class="example-tag example-json">circe</span> <span class="example-tag example-effects">cats-effect</span>

## Custom types

* [A demo of Tapir's capabilities using semi-auto derivation](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/custom_types/booksExampleSemiauto.scala) <span class="example-tag example-json">circe</span> <span class="example-tag example-client">sttp3</span> <span class="example-tag example-docs">Swagger UI</span> <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Pekko HTTP</span>
* [A query parameter which maps to a Scala 3 enum (enumeration)](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/custom_types/enumQueryParameter.scala) <span class="example-tag example-effects">Direct</span> <span class="example-tag example-server">Netty</span>
* [Handling comma-separated query parameters](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/custom_types/commaSeparatedQueryParameter.scala) <span class="example-tag example-docs">Swagger UI</span> <span class="example-tag example-effects">Direct</span> <span class="example-tag example-server">Netty</span>
* [Mapping a sealed trait hierarchy to JSON using a discriminator](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/custom_types/sealedTraitWithDiscriminator.scala) <span class="example-tag example-json">circe</span> <span class="example-tag example-docs">Swagger UI</span> <span class="example-tag example-effects">Direct</span> <span class="example-tag example-server">Netty</span>
* [Supporting custom types, when used in query or path parameters, as well as part of JSON bodies](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/custom_types/EndpointWithCustomTypes.scala) <span class="example-tag example-json">circe</span>

## Error handling

* [Customising errors that are reported on decode failures (e.g. invalid or missing query parameter)](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/errors/customErrorsOnDecodeFailurePekkoServer.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Pekko HTTP</span>
* [Error and successful outputs](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/errors/errorOutputsPekkoServer.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-json">circe</span> <span class="example-tag example-server">Pekko HTTP</span>
* [Error reporting provided by Iron type refinements](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/errors/IronRefinementErrorsNettyServer.scala) <span class="example-tag example-json">circe</span> <span class="example-tag example-effects">cats-effect</span> <span class="example-tag example-server">Netty</span>
* [Extending a base secured endpoint with error variants, using union types](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/errors/ErrorUnionTypesHttp4sServer.scala) <span class="example-tag example-json">circe</span> <span class="example-tag example-effects">cats-effect</span> <span class="example-tag example-server">http4s</span>

## JSON

* [Return a JSON body which optionally serializes as `null`](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/json/circeNullBody.scala) <span class="example-tag example-json">circe</span> <span class="example-tag example-effects">Direct</span> <span class="example-tag example-server">Netty</span>
* [Return a JSON response with Circe and auto-dervied codecs](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/json/circeAutoDerivationNettySyncServer.scala) <span class="example-tag example-json">circe</span> <span class="example-tag example-effects">Direct</span> <span class="example-tag example-server">Netty</span>
* [Return a JSON response with Jsoniter](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/json/jsoniterNettySyncServer.scala) <span class="example-tag example-json">jsoniter</span> <span class="example-tag example-effects">Direct</span> <span class="example-tag example-server">Netty</span>

## Logging

* [Logging using a correlation id](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/logging/ZioLoggingWithCorrelationIdNettyServer.scala) <span class="example-tag example-effects">ZIO</span> <span class="example-tag example-server">Netty</span>

## Multipart

* [Uploading a multipart form, with text and file parts](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/multipart/multipartFormUploadPekkoServer.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Pekko HTTP</span>

## Observability

* [Reporting DataDog metrics](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/observability/datadogMetricsExample.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-json">circe</span> <span class="example-tag example-server">Netty</span>
* [Reporting OpenTelemetry metrics](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/observability/openTelemetryMetricsExample.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-json">circe</span> <span class="example-tag example-server">Netty</span>
* [Reporting Prometheus metrics](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/observability/ZioMetricsExample.scala) <span class="example-tag example-effects">ZIO</span> <span class="example-tag example-server">ZIO HTTP</span>
* [Reporting Prometheus metrics](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/observability/prometheusMetricsExample.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-json">circe</span> <span class="example-tag example-server">Netty</span>

## OpenAPI documentation

* [Adding OpenAPI documentation extensions](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/openapi/openapiExtensions.scala) <span class="example-tag example-json">circe</span>
* [Documenting multiple endpoints](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/openapi/MultipleEndpointsDocumentationHttp4sServer.scala) <span class="example-tag example-docs">Swagger UI</span> <span class="example-tag example-effects">cats-effect</span> <span class="example-tag example-json">circe</span> <span class="example-tag example-server">http4s</span>
* [Documenting multiple endpoints](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/openapi/multipleEndpointsDocumentationPekkoServer.scala) <span class="example-tag example-docs">Swagger UI</span> <span class="example-tag example-effects">Future</span> <span class="example-tag example-json">circe</span> <span class="example-tag example-server">Pekko HTTP</span>
* [Exposing documentation using ReDoc](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/openapi/RedocZioHttpServer.scala) <span class="example-tag example-docs">ReDoc</span> <span class="example-tag example-effects">ZIO</span> <span class="example-tag example-json">circe</span> <span class="example-tag example-server">ZIO HTTP</span>
* [Exposing documentation using ReDoc](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/openapi/RedocContextPathHttp4sServer.scala) <span class="example-tag example-docs">ReDoc</span> <span class="example-tag example-effects">cats-effect</span> <span class="example-tag example-server">http4s</span>
* [Securing Swagger UI using OAuth 2](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/openapi/swaggerUIOAuth2PekkoServer.scala) <span class="example-tag example-docs">Swagger UI</span> <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Pekko HTTP</span>

## Schemas

* [Customising a derived schema, using annotations, and using implicits](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/schema/customisingSchemas.scala) <span class="example-tag example-docs">Swagger UI</span> <span class="example-tag example-effects">Future</span> <span class="example-tag example-json">circe</span> <span class="example-tag example-server">Netty</span>

## Security

* [CORS interceptor](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/security/corsInterceptorPekkoServer.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Pekko HTTP</span>
* [HTTP basic authentication](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/security/basicAuthenticationPekkoServer.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Pekko HTTP</span>
* [Interceptor verifying externally added security credentials](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/security/externalSecurityInterceptor.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Netty</span>
* [Login using OAuth2, authorization code flow](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/security/OAuth2GithubHttp4sServer.scala) <span class="example-tag example-effects">cats-effect</span> <span class="example-tag example-json">circe</span> <span class="example-tag example-server">http4s</span>
* [Separating security and server logic, with a reusable base endpoint](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/security/serverSecurityLogicPekko.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Pekko HTTP</span>
* [Separating security and server logic, with a reusable base endpoint, accepting & refreshing credentials via cookies](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/security/serverSecurityLogicRefreshCookiesPekko.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Pekko HTTP</span>
* [Separating security and server logic, with a reusable base endpoint, accepting & refreshing credentials via cookies](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/security/ServerSecurityLogicZio.scala) <span class="example-tag example-effects">ZIO</span> <span class="example-tag example-server">ZIO HTTP</span>

## Static content

* [Serving static files from a directory](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/static_content/staticContentFromFilesNettyServer.scala) <span class="example-tag example-effects">Direct</span> <span class="example-tag example-server">Netty</span>
* [Serving static files from a directory, with range requests](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/static_content/staticContentFromFilesPekkoServer.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Pekko HTTP</span>
* [Serving static files from resources](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/static_content/staticContentFromResourcesPekkoServer.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Pekko HTTP</span>
* [Serving static files secured with a bearer token](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/static_content/staticContentSecurePekkoServer.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Pekko HTTP</span>

## Status code

* [Serving static files from a directory](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/status_code/statusCodeNettyServer.scala) <span class="example-tag example-effects">Direct</span> <span class="example-tag example-server">Netty</span>

## Streaming

* [Proxy requests, handling bodies as fs2 streams](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/streaming/ProxyHttp4sFs2Server.scala) <span class="example-tag example-effects">cats-effect</span> <span class="example-tag example-server">http4s</span>
* [Respond with an fs2 stream, or with an error, represented as a failed effect in the business logic](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/streaming/StreamingHttp4sFs2ServerOrError.scala) <span class="example-tag example-effects">cats-effect</span> <span class="example-tag example-server">http4s</span>
* [Stream response as a Pekko stream](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/streaming/streamingPekkoServer.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Pekko HTTP</span>
* [Stream response as a ZIO stream](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/streaming/StreamingNettyZioServer.scala) <span class="example-tag example-effects">ZIO</span> <span class="example-tag example-server">Netty</span>
* [Stream response as a ZIO stream](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/streaming/StreamingZioHttpServer.scala) <span class="example-tag example-effects">ZIO</span> <span class="example-tag example-server">ZIO HTTP</span>
* [Stream response as an fs2 stream](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/streaming/StreamingHttp4sFs2Server.scala) <span class="example-tag example-effects">cats-effect</span> <span class="example-tag example-server">http4s</span>
* [Stream response as an fs2 stream](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/streaming/StreamingNettyFs2Server.scala) <span class="example-tag example-effects">cats-effect</span> <span class="example-tag example-server">Netty</span>

## Testing

* [Test endpoints using the MockServer client](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/testing/SttpMockServerClientExample.scala) <span class="example-tag example-json">circe</span>
* [Test endpoints using the TapirStubInterpreter](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/testing/CatsServerStubInterpreter.scala) <span class="example-tag example-effects">cats-effect</span>
* [Test endpoints using the TapirStubInterpreter](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/testing/PekkoServerStubInterpreter.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Pekko HTTP</span>

## WebSocket

* [A WebSocket chat across multiple clients connected to the same server](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/websocket/WebSocketChatNettySyncServer.scala) <span class="example-tag example-effects">Direct</span> <span class="example-tag example-server">Netty</span>
* [Describe and implement a WebSocket endpoint](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/websocket/WebSocketNettySyncServer.scala) <span class="example-tag example-effects">Direct</span> <span class="example-tag example-server">Netty</span>
* [Describe and implement a WebSocket endpoint](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/websocket/webSocketPekkoServer.scala) <span class="example-tag example-effects">Future</span> <span class="example-tag example-server">Pekko HTTP</span>
* [Describe and implement a WebSocket endpoint](https://github.com/softwaremill/tapir/tree/master//examples/src/main/scala/sttp/tapir/examples/websocket/WebSocketHttp4sServer.scala) <span class="example-tag example-docs">AsyncAPI</span> <span class="example-tag example-effects">cats-effect</span> <span class="example-tag example-json">circe</span> <span class="example-tag example-server">http4s</span>