# Migrating

## From 0.20 to 1.0

* `EndpointVerifier` is moved to a separate `tapir-testing` module
* `customJsonBody` is renamed to `customCodecJsonBody`
* `anyFromStringBody` is renamed to `stringBodyAnyFormat`
* `anyFromUtf8StringBody` is renamed to `stringBodyUtf8AnyFormat`
* `CustomInterceptors` is renamed to `CustomiseInterceptors` as this better reflects the functionality of the class
* `CustomiseInterceptors.errorOutput` is renamed to `.defaultHandlers`, with additional options added.
* in custom server interpreters, the `RejectInterecptor` must be now disabled explicitly using `RejectInterceptor.disableWhenSingleEndpoint` when a single endpoint is being interpreted; the `ServerInterpreter` no longer knows about all endpoints, as it is now parametrised with a function which gives the potentially matching endpoints, given a `ServerRequest`
* the names of Prometheus and OpenTelemetry metrics have changed; there are now three metrics (requests active, total and duration), instead of the previous 4 (requests active, total, response total and duration). Moreover, the request duration metric includes an additional label - phase (either headers or body), measuring how long it takes to create the headers or the body.
* `CustomiseInterceptors.appendInterceptor` is replaced with `.addInterceptor`; `.prependInterceptor` and `.appendInterceptor` methods are also added
* `RequestHandler`, returned by `RequestInterceptor`, now also accepts a list of server endpoints. This allows to dynamically filter the endpoints. Moreover, there's a new type parameter in `RequestInterceptor` and `RequestHandler`, `R`, specifying the capabilities required by the given server endpoints.
* the http4s server interpreters have only one effect parameter, instead of two (`F` for the general effect and `G` for the body effect). This separation stopped making sense with the introduction of `BodyListener` some time ago and keeping `ServerInterpreter` using a single effect.
* the Swagger and Redoc UIs by default use relative paths for yaml/json documentation references and for redirects. This can be changed by passing appropriate options.
* OpenAPI and AsyncAPI models are now part of a separate sttp-apispec project, hence the packages of these objects changed as well, from `sttp.tapir.apispec` / `sttp.tapir.openapi` / `sttp.tapir.asyncapi` to `sttp.tapir.apispec.(...)`
* The `streamBinaryBody` method now has a mandatory `format` parameter, which previously was fixed to be `CodecFormat.OctetStream()`
* server interpreters sources are now grouped based on the underlying server implementation (e.g. http4s, vertx), and then sub-directories contain effect integrations (e.g. cats, zio). The template for artifact naming is `tapir-<server>-server-<effect>`. E.g. `tapir-zio-http4s-server` became `tapir-http4s-server-zio1`

### Moved traits, classes, objects

* server interpreters & interceptors have moved from `core` into the `server/core` module
* `ServerResponse` and `ValuedEndpointOutput` are moved to `sttp.tapir.server.model`
* metrics classes and interceptors have moved to the `sttp.tapir.server.metrics` package
* `Endpoint.renderPathTemplate` is renamed to `Endpoint.showPathTemplate`
* web socket exceptions `UnsupportedWebSocketFrameException` and `WebSocketFrameDecodeFailure` are now in the `sttp.tapir.model` package

## From 0.19 to 0.20

See the [release notes](https://github.com/softwaremill/tapir/releases/tag/v0.20.0)

## From 0.18 to 0.19

See the [release notes](https://github.com/softwaremill/tapir/releases/tag/v0.19.0)

## From 0.17 to 0.18

See the [release notes](https://github.com/softwaremill/tapir/releases/tag/v0.18.0)