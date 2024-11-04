# 3. Using JSON bodies

```{note}
The tutorial is also available [as a video](https://www.youtube.com/watch?v=NG8XWS7ijHU).
```

The endpoints we defined in the previous tutorials all used `String` bodies. Quite naturally, tapir supports
much more than that - using appropriate **codecs**, it's possible to serialize and deserialize to arbitrary types.
The most popular format on the web is JSON; hence, let's see how to expose a JSON-based endpoint using tapir.

Tapir's support for JSON is twofold. First, we've got integrations with various JSON libraries, which provide the
logic of converting between a `String` (that's read from the network) and a high-level type, such as a `case class`.
Second, we've got the generation of **schemas**, which describe the high-level types. Schemas are used for
documentation (so that our endpoints are described in OpenAPI accurately), and for validation of incoming requests.

## Deriving JSON codecs

First, we need to pick a JSON library. There's a lot to choose from, but we'll go with [jsoniter](https://github.com/plokhotnyuk/jsoniter-scala),
the fastest JSON library for Scala. We'll need to add a dependency, which will help us in defining the JSON codecs -
we'll see how in a moment:

```scala
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.30.1
```

Once we have that, let's define our data model, which we'll use for requests and responses. We'll define a single 
endpoint, transforming a `Meal` instance into a `Nutrition` one:

{emphasize-lines="3-4"}
```scala
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.30.1

case class Meal(name: String, servings: Int, ingredients: List[String])
case class Nutrition(name: String, healthy: Boolean, calories: Int)
```

The first step is to define the functions that will enable the serialization and deserialization of these classes to 
JSON. This can be done by hand, but most of the time, we can rely on derivation: a compile-time process that generates 
the code needed to transform a `String` into a `Meal` (or an error) and to transform a `Nutrition` into a `String.`

This is the task of our chosen JSON library. By adding a `... derives` clause, an instance of a `JsonValueCodec` will
be generated at compile-time (with compile-time errors if the library can't figure out how to serialize/deserialize some
component).

We can also test the serialization of an example object. Let's put this in a `json.scala` file:

```scala
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.30.1

import com.github.plokhotnyuk.jsoniter_scala.core.* // needed for `writeToString`
import com.github.plokhotnyuk.jsoniter_scala.macros.* // needed for ... derives

case class Meal(name: String, servings: Int, ingredients: List[String]) 
  derives ConfiguredJsonValueCodec
case class Nutrition(name: String, healthy: Boolean, calories: Int) 
  derives ConfiguredJsonValueCodec

@main def tapirJson(): Unit =
  println(writeToString(Meal("salad", 1, List("lettuce", "tomato", "cucumber"))))
```

```{note}
Even though we request derivation of a `ConfiguredJsonValueCodec`, we obtain a `JsonValueCodec` instance. This is due to
the way jsoniter-scala works; the "configured" variant accepts an implicit `CodecMakerConfig`, which can be used to 
customize the (de)serialization process (`snake_case` vs `camelCase`, handling nulls, etc.). 
```

This should output the following:

```bash
% scala-cli json.scala
{"name":"salad","servings":1,"ingredients":["lettuce","tomato","cucumber"]}
```

## Deriving schema

With the functions translating between JSON strings and our high-level types ready, we can take care of the second
component: schemas. As mentioned in the beginning, schemas are needed to generate accurate OpenAPI documentation and
validation. They can be defined by hand, but most of the time, you can use compile-time derivation - just as with
JSON codecs.

In our case, deriving the schemas will amount to adding a `... derives Schema` clause. Let's run a quick test:

{emphasize-lines="1, 7, 10, 12, 16"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.30.1

import com.github.plokhotnyuk.jsoniter_scala.core.*   // needed for `writeToString`
import com.github.plokhotnyuk.jsoniter_scala.macros.* // needed for ... derives

import sttp.tapir.* // needed for `Schema`

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
{"name":"salad","servings":1,"ingredients":["lettuce","tomato","cucumber"]}
Schema(SProduct(List(SProductField(FieldName(name,name),Schema(SString(),None,false,None,None,None,None,false,false,All(List()),AttributeMap(Map()))), SProductField(FieldName(servings,servings),Schema(SInteger(),None,false,None,None,Some(int32),None,false,false,All(List()),AttributeMap(Map()))), SProductField(FieldName(ingredients,ingredients),Schema(SArray(Schema(SString(),None,false,None,None,None,None,false,false,All(List()),AttributeMap(Map()))),None,true,None,None,None,None,false,false,All(List()),AttributeMap(Map()))))),Some(SName(.Meal,List())),false,None,None,None,None,false,false,All(List()),AttributeMap(Map()))
```

As you can see, the string representation of the schema isn't the most beautiful, but its primary purpose is to be
consumed by interpreters (e.g., the documentation interpreter), not by human beings.

## Exposing the endpoint

We can now expose a JSON-based endpoint with both JSON codes and schemas in place. We'll try to read a `Meal` instance
from the request and write a `Nutrition` instance as a response. In order to do this, we'll need to add a dependency
which provides `tapir` <-> `jsoniter-scala` integration.

The integration defines a `jsonBody` method that creates a description of a JSON body, which can be used both as an 
endpoint input and output.

To create a `jsonBody[T]`, both a JSON codec and a `Schema[T]` must be in scope - and that's the case since these
values are attached to the companion objects of `Meal` and `Nutrition`, thanks to the `... derives` mechanism. Notice
how the `jsonBody[T]` method is used in the endpoint definition. We'll also expose Swagger UI documentation:

{emphasize-lines="2-4, 10-15, 23-39"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-jsoniter-scala:1.11.8
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.30.1

import com.github.plokhotnyuk.jsoniter_scala.macros.* // needed for ... derives

import sttp.tapir.*
import sttp.tapir.json.jsoniter.* // needed for jsonBody[T]
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.shared.Identity

import scala.util.Random

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
    .handleSuccess { meal => 
      Nutrition(meal.name, random.nextBoolean(), random.nextInt(1000)) 
    }

  val swaggerEndpoints = SwaggerInterpreter()
    .fromServerEndpoints[Identity](List(mealEndpoint), "My App", "1.0")
 
  NettySyncServer().port(8080)
    .addEndpoint(mealEndpoint)
    .addEndpoints(swaggerEndpoints)
    .startAndWait()
```

We can now test the endpoint both from the command line and via the browser:

```bash
# first console
% scala-cli json.scala

# another console
% curl -XPOST "http://localhost:8080" -d '{"name": "salad", "servings": 1, "ingredients": ["lettuce", "tomato", "cucumber"]}'
{"name":"salad","healthy":true,"calories":42}

# Now open http://localhost:8080/docs in your browser and browse the generated documentation!
```

Try to provide some invalid JSON values - you should see `400 Bad Request` responses.

## More on JSON

To find out more about schema derivation and JSON support in tapir, the following reference documentation pages might
be useful:

* [](../endpoint/schemas.md)
* [](../endpoint/json.md)
