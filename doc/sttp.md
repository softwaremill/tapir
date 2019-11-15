## Using as an sttp client

Add the dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-sttp-client" % "0.12.1"
```

To make requests using an endpoint definition using the [sttp client](https://github.com/softwaremill/sttp), import:

```scala
import sttp.tapir.client.sttp._
```

This adds the two extension methods to any `Endpoint`:
 - `toSttpRequestUnsafe(Uri)` for given base URI returns a function which throws exception if deserialization fails
    ```scala
    I => Request[Either[E, O], Nothing]
    ```
 - `toSttpRequest(Uri)` for given base URI returns a function which encapsulates decoding errors within DecodeResult class
    ```scala
    I => Request[DecodeResult[Either[E, O]], Nothing]
    ```

Note that this are a one-argument functions, where the single argument is the input of end endpoint. This might be a 
single type, a tuple, or a case class, depending on the endpoint description. 

After providing the input parameters, a description of the request to be made is returned. This can be further 
customised and sent using any sttp backend.

See  the [runnable example](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/BooksExample.scala)
for example usage.
