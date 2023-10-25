# gRPC

Experimental feature - it's not complete and API may change

tapir supports currently defining and exposing gRPC endpoints. There is also a handy tool for generating proto files
from these endpoints' definitions.

## Modules

* `protobuf` - contains a Protobuf protocol model and utilities that make generating proto files handy. This module
  should be used for generating proto files from endpoints definitions
* `pbDirectProtobuf` - integration with `PBDirect` library. Should be used for auto codecs derivation. Currently, it's
  necessary to add this module for generating proto files with the `protobuf` module.
* `akkaGrpcServer` - a module that provides `AkkaGrpcServerInterpreter` implementation. It should be used to serve tapir
  grpc endpoints.
* `pekkoGrpcServer` - a module that provides `PekkoGrpcServerInterpreter` implementation. It can be used as an
  alternative to `akkaGrpcServer`.
* `grpcExamples` - contains example use cases

## Defining endpoints

Every gRPC endpoint requires specifying the following values: service name, method name, input format,
output format.

In tapir, we can simply pass service and method names as strings separated with `/` to the tapir endpoint input
definition e.g. `endpoint.in("Library" / "AddBook")` where `"Library"` is a service name and `"AddBook"` is a method
name.

Definition of an endpoint's inputs and output format is very similar to the one that we use for JSONs. We can use an
endpoint body constructor helper `sttp.tapir.grpc.protobuf.pbdirect.grpcBody[T]` that based on the type `T` create a
body definition that can be passed to as input or output of a given endpoint
e.g. `endpoint.in(grpcBody[AddSimpleBook]).out(grpcBody[SimpleBook])`.
Mapping for basic types are defined (e.g. `java.lang.String` -> `string`), but the target protobuf type can be simply customized via `.attribute` schema feature 
(e.g. `implicit newSchema = implicitly[Derived[Schema[SimpleBook]]].value.modify(_.title)(_.attribute(ProtobufAttributes.ScalarValueAttribute, ProtobufScalarType.ProtobufBytes))`

Currently, the only supported protocol is protobuf. On the server side, we use
a [PBDirect](https://github.com/47degrees/pbdirect) library for encoding and decoding messages. It derives codecs from a
class definition which means we do not depend on the generated code from a proto file.

The `grpcBody` function takes as an implicit param tapir schema definition, that is used to generate a proto file, and
PBDirect codecs for a given type.
Currently, we don't support passing any metadata.

## Generating proto file

With defined endpoints, we can move towards generating a proto file that we could use to generate clients for our API.

The easiest way to do it is by using a built-in `ProtoSchemaGenerator`. We need to only choose a path for the file,
package name, pass endpoints definitions, and finally invoke the `renderToFile` function e.g.

```
ProtoSchemaGenerator.renderToFile(
    path = "grpc/examples/src/main/protobuf/main.proto",
    packageName = "sttp.tapir.grpc.examples.gen",
    endpoints = Endpoints.endpoints
)
```

## gRPC server

With the proto file, we can generate clients for our API or the server-side code
using [ScalaPb](https://scalapb.github.io) library.

It's possible to connect the generated server code to tapir endpoints definitions, but it's not convenient since depends
on auto-generated code. We strongly recommend using the new dedicated server interpreter `AkkaGrpcServerInterpreter`.
It's built on top of `AkkaHttpServerInterpreter` and provides support for encoding and decoding HTTP2 binary messages.

[Here](https://github.com/softwaremill/tapir/blob/master/grpc/examples/src/main/scala/sttp/tapir/grpc/examples/GrpcSimpleBooksExample.scala)
you can find a simple example.

It's worth mentioning that by adjusting slightly encoders/decoders it's possible to expose gRPC endpoints with 
`AkkaHttpServerInterpreter` as simple HTTP endpoints. This approach is not recommended, because it does not support
transmitting multiple messages in a single http request.

## Supported data formats
* Basic scalar types 
* Collections (repeated values)
* Top level and nested products
* Tapir schema derivation for coproducts (sealed traits) is supported, but we're missing codecs on the pbdirect side out of the box (oneof)