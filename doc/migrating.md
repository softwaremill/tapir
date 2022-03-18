# Migrating

## From 0.20 to 1.0

* in custom server interpreters, the `RejectInterecptor` must be now disabled explicitly using `RejectInterceptor.disableWhenSingleEndpoint` when a single endpoint is being interpreted; the `ServerInterpreter` no longer knows about all endpoints, as it is now parametrised with a function which gives the potentially matching endpoints, given a `ServerRequest`

### Moved traits, classes, objects

* server interpreters & interceptors have moved from `core` into the `server/core` module
* `ServerResponse` and `ValuedEndpointOutput` are moved to `sttp.tapir.server.model`
* metrics classes and interceptors have moved to the `sttp.tapir.server.metrics` package
* `Endpoint.renderPathTemplate` is renamed to `Endpoint.showPathTemplate`
* web socket exceptions `UnsupportedWebSocketFrameException` and `WebSocketFrameDecodeFailure` are now in the `sttp.tapir.model` package