# Migrating

## From 0.20 to 1.0

### Moved traits, classes, objects

* server interpreters & interceptors have moved from `core` into the `server/core` module
* `ServerResponse` and `ValuedEndpointOutput` are moved to `sttp.tapir.server.model`
* metrics classes and interceptors have moved to the `sttp.tapir.server.metrics` package
* `Endpoint.renderPathTemplate` is renamed to `Endpoint.showPathTemplate`
* web socket exceptions `UnsupportedWebSocketFrameException` and `WebSocketFrameDecodeFailure` are now in the `sttp.tapir.model` package