# Streaming support

Both input and output bodies can be mapped to a stream, by using `stream[*]Body(streams)`. The parameter `streams` 
must implement the `Streams[S]` capability, and determines the precise type of the binary stream supported by the given
non-blocking streams implementation. The interpreter must then support the given capability. Refer to the documentation 
of server/client interpreters for more information.

```{note}
Here, streams refer to asynchronous, non-blocking, "reactive" stream implementations, such as [akka-streams](https://doc.akka.io/docs/akka/current/stream/index.html),
[fs2](https://fs2.io) or [zio-streams](https://zio.dev/docs/datatypes/datatypes_stream). If you'd like to use
blocking streams (such as `InputStream`), these are available through e.g. `inputStreamBody` without any 
additional requirements on the interpreter.
```

Adding a stream body input/output influences both the type of the input/output, as well as the 5th type parameter
of `Endpoint`, which specifies the requirements regarding supported stream types for interpreters.

When using a stream body, a schema must be provided for documentation. By default, when using `streamBinaryBody`,
the schema will simply be that of a binary body. If you have a textual stream, you can use `streamTextBody`. In that
case, you'll also need to provide the default format (media type) and optional charset to be used to determine the
content type. 

To provide an arbitrary schema, use `streamBody`. Note, however, that this schema will only be used
for generating documentation. The incoming stream data will not be validated using the schema validators.

For example, to specify that the output is an akka-stream, which is a (presumably large) serialised list of json objects 
mapping to the `Person` class:  

```scala mdoc:silent:reset
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.capabilities.pekko.PekkoStreams

case class Person(name: String)

// copying the derived json schema type
endpoint.out(streamBody(PekkoStreams)(Schema.derived[List[Person]], CodecFormat.Json()))
```

See also the [runnable streaming example](../examples.md). 

## Next

Read on about [web sockets](websockets.md).
