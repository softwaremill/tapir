# Overview of server integrations

Server interpreters require the endpoint descriptions to be combined with "business logic": functions, which compute 
an endpoint's output parameters based on input parameters.

Tapir integrates with a number of HTTP server implementations, through **server interpreters**. We recommend starting 
with the Netty-based server. However, if you already have experience with another server, or are using one in your 
project already, just continue doing so, and enjoy seamless Tapir integration! 

Currently supported:
* [Netty](netty.md) (using direct-style, `Future`s, cats-effect or ZIO)
* [Http4s](http4s.md) `HttpRoutes[F]` (using cats-effect or [ZIO](server/zio-http4s.md))
* [Pekko HTTP](pekkohttp.md) `Route`s/`Directive`s
* [ZIO Http](ziohttp.md) `Http`
* [Play](play.md) `Route`
* [Akka HTTP](akkahttp.md) `Route`s/`Directive`s
* [Helidon Níma](nima.md) (using JVM 21 Virtual Threads and direct style)
* [Finatra](finatra.md) `http.Controller`
* [Vert.X](vertx.md) `Router => Route` (using `Future`s, cats-effect or ZIO)
* [Armeria](armeria.md) `HttpServiceWithRoutes` (using `Future`s, cats-effect or ZIO)
* [JDK HTTP](jdkhttp.md) `HttpHandler` (simple, synchronous API only)
* [aws](aws.md) through Lambda/SAM/Terraform
* [gRPC](../other/grpc.md)
