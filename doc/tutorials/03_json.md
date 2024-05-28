# Using JSON bodies

The endpoints that we've defined in the previous tutorials all used `String` bodies. Quite naturally, tapir supports 
much more than that - using appropriate **codecs**, it's possible to serialise and deserialise to arbitrary types.
The most popular format on the web is JSON, hence let's see how to expose a JSON-based endpoint using tapir.

Tapir's support for JSON is twofold. First, we've got integrations with various JSON libraries, which provide the
logic of converting between a `String` (that's read from the network), and a high-level type, such as a `case class`.
Second, we've got the generation of **schemas**, which describe the high-level types. Schemas are used for 
documentation (so that our endpoints are described in OpenAPI accurately), and for validation of incoming requests.

## Deriving JSON codecs

First, we need to pick a JSON library. There's a lot to choose from, but we'll go with [jsoniter](https://github.com/plokhotnyuk/jsoniter-scala),
which is the fastest JSON library for Scala. We'll need to add a dependency:

```scala
//> using dep com.softwaremill.sttp.tapir::tapir-jsoniter-scala-bundle:@VERSION@
```

This brings in both the `jsoniter-scala` library, as well as the `tapir` <-> `jsoniter-scala` integration. Once we
have that, let's define our data model, which we'll use for requests and responses. We'll define a single endpoint,
transforming a `Meal` class into a `Nutrition` one:

```scala
case class Meal(name: String, servings: Int, ingredients: List[String])
case class Nutrition(name: String, healthy: Boolean, calories: Int)
```

The first step is to define the functions, which will be able to serialise and deserialise these classes to JSON. This
can be done by hand, but most of the time we can rely on derivation: a compile-time process which generates the
code, needed to transform a `String` into a `Meal` (or an error), and to transform a `Nutrition` into a `String`.

This is the task of our chosen JSON library. By adding a `... derives` clause, an instance of a `JsonValueCodec` will
be generated at compile-time (with compile-time errors if the library can't figure out how to serialise/deserialise some
component). 

```{note}
Since jsoniter-scala doesn't support `derives ...` out of the box, the tapir integration provides this as an add-on, 
through the `tapir-jsoniter-scala-bundle` dependency. That's why we add a `... derives ConfiguredJsonValueCodec` clause,
but we obtain a `JsonValueCodec` instance as a result. 
```

We can also test the serialisation of an example object. Let's put this in a `json.scala` file:

```scala
//> using dep com.softwaremill.sttp.tapir::tapir-jsoniter-scala-bundle:@VERSION@

import sttp.tapir.json.jsoniter.bundle.* // needed for ... derives ...
import com.github.plokhotnyuk.jsoniter_scala.core.* // needed for `writeToString`

case class Meal(name: String, servings: Int, ingredients: List[String]) 
  derives ConfiguredJsonValueCodec
case class Nutrition(name: String, healthy: Boolean, calories: Int) 
  derives ConfiguredJsonValueCodec

@main def tapirJson(): Unit =
  println(writeToString(Meal("salad", 1, List("lettuce", "tomato", "cucumber"))))
```

This should output the following:

```bash
% scala-cli json.scala

```

## Deriving schema

With the functions translating between JSON strings and our high-level types ready, we can take care of the second 
component: schemas. As mentioned in the beginning, schemas are needed to generate accurate OpenAPI documentation, and
for validation. They can be defined by hand, but most of the time compile-time derivation - just as with JSON codecs.

In our case, deriving the schemas will amount to adding a `... derives Schema` clause. Let's run a quick test:

{emphasize-lines="3, 8, 10"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-jsoniter-scala-bundle:@VERSION@

import sttp.tapir.*
import sttp.tapir.json.jsoniter.bundle.* // needed for ... derives ...
import com.github.plokhotnyuk.jsoniter_scala.core.* // needed for `writeToString`

case class Meal(name: String, servings: Int, ingredients: List[String]) 
  derives ConfiguredJsonValueCodec, Schema
case class Nutrition(name: String, healthy: Boolean, calories: Int) 
  derives ConfiguredJsonValueCodec, Schema

@main def tapirJson(): Unit =
  println(writeToString(Meal("salad", 1, List("lettuce", "tomato", "cucumber"))))
  println(summon[Schema[Meal]])
```

When run, we additionally get the schema:

```bash
% scala-cli json.scala

```

As you can see, the string representation of the schema isn't the most beautiful, but its primary purpose is to be
consumed by interpreters (e.g. the documentation interpreter), not by human beings.

## Exposing the endpoint

With both JSON codes and schemas in place, we can now expose a JSON-based endpoint. The `Meal` will be the required
request's body, and `Nutrition` will be the response. The `jsonBody` method creates a description of a JSON body, which
can be used both as an input, and as an output.

To create a `jsonBody[T]`, both a JSON codec and a `Schema[T]` must be in scope - and that's the case, since these 
values are attached to the companion objects of `Meal` and `Nutrition`, as we have used the `... derives` mechanism.
Notice how it's being used in the endpoint definition. We'll also expose Swagger UI documentation:

{emphasize-lines="1-3, 15-25"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:@VERSION@
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:@VERSION@
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:@VERSION@
//> using dep com.softwaremill.sttp.tapir::tapir-jsoniter-scala-bundle:@VERSION@

import sttp.tapir.*
import sttp.tapir.json.jsoniter.bundle.* // needed for ... derives ...
import scala.util.random

case class Meal(name: String, servings: Int, ingredients: List[String])
  derives ConfiguredJsonValueCodec, Schema
case class Nutrition(name: String, healthy: Boolean, calories: Int)
  derives ConfiguredJsonValueCodec, Schema

@main def tapirJson(): Unit = 
  val random = new Random
  
  val mealEndpoint = endpoint.post
    .in(jsonBody[Meal])
    .out(jsonBody[Nutrition])
    // plugging in AI is left as an exercise to the reader
    .handle { meal => Nutrition(meal.name, random.nextBoolean(), random.nextInt(1000)) }

  val swaggerEndpoints = SwaggerInterpreter().fromEndpoints[Identity](List(mealEndpoint), "My App", "1.0")
 
  NettySyncServer().port(8080)
    .addEndpoint(mealEndpoint)
    .addEndpoints(swaggerEndpoints)
    .startAndWait()
```

We can now test the endpoint both from the command line, and via the browser:

```bash
# first console
% scala-cli json.scala

# another console
% curl -XPOST "http://localhost:8080" -d '{"name": "salad", "servings": 1, "ingredients": ["lettuce", "tomato", "cucumber"]}'
{"name":"salad","healthy":true,"calories":42}

# Now open http://localhost:8080/docs in your browser, and browse the generated documentation!
```

Try to provide some invalid JSON values - you should see `400 Bad Request` responses.

## More on JSON

To find out more on schema derivation and JSON support in tapir, the following reference documentation pages might
be useful:

* [Schema derivation](../endpoint/schemas.md)
* [Working with JSON](../endpoint/json.md)
