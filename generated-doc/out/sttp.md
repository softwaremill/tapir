## Using as an sttp client

Add the dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-sttp-client" % "0.16.13"
```

To make requests using an endpoint definition using the [sttp client](https://github.com/softwaremill/sttp), import:

```scala
import sttp.tapir.client.sttp._
```

This adds the two extension methods to any `Endpoint`:
 - `toSttpRequestUnsafe(Uri)`: given the base URI returns a function, which might throw an exception if 
   decoding of the result fails
   ```scala
   I => Request[Either[E, O], Nothing]
   ```
 - `toSttpRequest(Uri)`: given the base URI returns a function, which represents decoding errors as the `DecodeResult` 
   class
   ```scala
   I => Request[DecodeResult[Either[E, O]], Nothing]
   ```

Note that these are a one-argument functions, where the single argument is the input of end endpoint. This might be a 
single type, a tuple, or a case class, depending on the endpoint description. 

After providing the input parameters, a description of the request to be made is returned, with the input value
encoded as appropriate request parameters: path, query, headers and body. This can be further 
customised and sent using any sttp backend. The response will then contain the decoded error or success values
(note that this can be the body enriched with data from headers/status code).

See  the [runnable example](https://github.com/softwaremill/tapir/blob/master/examples/src/main/scala/sttp/tapir/examples/BooksExample.scala)
for example usage.
