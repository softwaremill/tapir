# Examples

The [`examples`](https://github.com/softwaremill/tapir/tree/master/examples/src/main/scala/sttp/tapir/examples) sub-project contains a number of runnable tapir usage examples:

* [Hello world server, using akka-http](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/HelloWorldAkkaServer.scala)
* [Hello world server, using http4s](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/HelloWorldHttp4sServer.scala)
* [Separate error & success outputs, using akka-http](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/ErrorOutputsAkkaServer.scala)
* [Multiple endpoints, exposing OpenAPI/Swagger documentation, using akka-http](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/MultipleEndpointsDocumentationAkkaServer.scala)
* [Multiple endpoints, exposing OpenAPI/Swagger documentation, using http4s](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/MultipleEndpointsDocumentationHttp4sServer.scala)
* [Multiple endpoints, with the description coupled with server logic, using akka-http](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/MultipleServerEndpointsAkkaServer.scala)
* [Reporting errors in a custom format when a query/path/.. parameter cannot be decoded](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/CustomErrorsOnDecodeFailureAkkaServer.scala)
* [Using custom types in endpoint descriptions](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/EndpointWithCustomTypes.scala)
* [Multipart form upload, using akka-http](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/MultipartFormUploadAkkaServer.scala)
* [Basic Authentication, using akka-http](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/BasicAuthenticationAkkaServer.scala)
* [Partial server logic (authentication), using akka-http](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/PartialServerLogicAkka.scala)
* [Authorization using Github (OAuth2) with http4s](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/OAuth2GithubHttp4sServer.scala)
* [Books example](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/BooksExample.scala)
* [ZIO example, using http4s](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/ZioExampleHttp4sServer.scala)
* [ZIO example with environment, using http4s](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/ZioEnvExampleHttp4sServer.scala)
* [ZIO partial server logic example, using http4s](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/ZioPartialServerLogicHttp4s.scala)
* [Streaming body, using akka-http](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/StreamingAkkaServer.scala)
* [Streaming body, using http4s + fs2](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/StreamingHttp4sFs2Server.scala)
* [Web sockets server, using akka-http](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/WebSocketAkkaServer.scala)
* [Web sockets client, using akka-http](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/WebSocketAkkaClient.scala)
* [Web sockets server, using http4s](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/WebSocketHttp4sServer.scala)

## Other examples

To see an example project using Tapir, [check out this Todo-Backend](https://github.com/hejfelix/tapir-http4s-todo-mvc) 
using tapir and http4s.

For a quick start using http4s and tapir you can use [a gitter8 template](https://codeberg.org/wegtam/http4s-tapir.g8).
A new project can be created using: `sbt new https://codeberg.org/wegtam/http4s-tapir.g8.git`

## Blogs, articles

* [Designing tapir's WebSockets support](https://blog.softwaremill.com/designing-tapirs-websockets-support-ff1573166368)
* [Three easy endpoints](https://blog.softwaremill.com/three-easy-endpoints-a6cbd52b0a6e)
* [tAPIr’s Endpoint meets ZIO’s IO](https://blog.softwaremill.com/tapirs-endpoint-meets-zio-s-io-3278099c5e10)
* [Describe, then interpret: HTTP endpoints using tapir](https://blog.softwaremill.com/describe-then-interpret-http-endpoints-using-tapir-ac139ba565b0)
* [Functional pancakes](https://blog.softwaremill.com/functional-pancakes-cf70023f0dcb)

## Videos

* [ScalaWorld 2019: Designing Programmer-Friendly APIs](https://www.youtube.com/watch?v=I3loMuHnYqw)
