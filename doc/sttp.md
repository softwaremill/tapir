## Using as an sttp client

Add the dependency:

```scala
"com.softwaremill.tapir" %% "tapir-sttp-client" % "0.4"
```

To make requests using an endpoint definition using [sttp](https://github.com/softwaremill/sttp), import:

```scala
import tapir.client.sttp._
```

This adds the `toRequest(Uri)` extension method to any `Endpoint` instance which, given the given base URI returns a 
function:

```scala
[I as function arguments] => Request[Either[E, O], Nothing]
```

After providing the input parameters, the result is a description of the request to be made, which can be further 
customised and sent using any sttp backend.

See  the [runnable example](https://github.com/softwaremill/tapir/blob/master/playground/src/main/scala/tapir/example/BooksExample.scala)
for example usage.