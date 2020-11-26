# Streaming support

Both input and output bodies can be mapped to a stream, by using `streamBody(streams)`. The parameter `streams` must 
implement the `Streams[S]` capability, and determines the precise type of the binary stream supported by the given
non-blocking streams implementation. The interpreter must then support the given capability. Refer to the documentation 
of server/client interpreters for more information.

```eval_rst
.. note::

  Here, streams refer to asynchronous, non-blocking, "reactive" stream implementations, such as `akka-streams <https://doc.akka.io/docs/akka/current/stream/index.html>`_,
  `fs2 <https://fs2.io>`_ or `zio-streams <https://zio.dev/docs/datatypes/datatypes_stream>`_. If you'd like to use
  blocking streams (such as ``InputStream``), these are available through e.g. ``inputStreamBody`` without any 
  additional requirements on the interpreter.
```

Adding a stream body input/output influences both the type of the input/output, as well as the 4th type parameter
of `Endpoint`, which specifies the requirements regarding supported stream types for interpreters.

When using a stream body, the schema (needed for documentation) and format (media type) of the body must be provided by 
hand, as they cannot be inferred from the raw stream type. For example, to specify that the output is an akka-stream, 
which is a (presumably large) serialised list of json objects mapping to the `Person` class:  

```scala mdoc:silent:reset
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.capabilities.akka.AkkaStreams
import akka.stream.scaladsl._
import akka.util.ByteString

case class Person(name: String)

endpoint.out(streamBody(AkkaStreams, schemaFor[List[Person]], CodecFormat.Json()))
```

See also the [runnable streaming example](../examples.md). 

## Next

Read on about [web sockets](websockets.md).