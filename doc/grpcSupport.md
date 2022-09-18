# gRPC support

Experimental feature - it's not complete and API may change

tAPIr supports currently defining gRPC endpoints and generating proto files from these definitions.
## Defining endpoints
The definition of an endpoint requires names for service and method. We can simply pass them as an input path the endpoint definitions e.g. `endpoint.in("Library" / "AddBook")`.
Defined that we can decide on the message format for input and output of the endpoint. Currently, the only supported way for encoding and decoding messages is using the PBdirect library. It can be done via a built-in integration e.g. `endpoint.in(grpcBody[SimpleBook]).out(grpcBody[SimpleBook])`.
Currently, we don't support passing any metadata.

##Generating proto file
With such a defined endpoint we can move towards generating a proto file that we could use to generate clients for our API. The easiest way to do it is by using a built-in `ProtoSchemaGenerator`. We need to only choose a path for the file, package name, pass endpoints definitions and finally invoke generate function e.g.
```
object MainGenerator extends ProtoSchemaGenerator {
  val path: String = "grpc/examples/src/main/protobuf/main.proto"
  val endpoints = Endpoints.es
  val packageName = "sttp.tapir.grpc.examples.gen"

  generate()
}
```
## gRPC server
Having the proto file we can use it to generate clients for our API or the server-side code. The other option is possible to use with tAPIr but is not convenient.  We strongly recommend using the new dedicated server interpreter `AkkaGrpcServerInterpreter`. It's built on top of `AkkaHttpServerInterpreter`, but provides support for encoding and decoding HTTP2 binary messages.

Simple example: src/main/scala/sttp/tapir/grpc/examples/GrpcSimpleBooksExample.scala