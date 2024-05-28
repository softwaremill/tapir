# Using JSON bodies

The endpoints that we've defined in the previous tutorials all used `String` bodies. Quite naturally, tapir supports 
much more than that - using appropriate **codecs**, it's possible to serialise and deserialise to arbitrary types.
The most popular format on the web is JSON, hence let's see how to expose a JSON-based endpoint using tapir.

Tapir's support for JSON is twofold. First, we've got integrations with various JSON libraries, which provide the
logic of converting between a `String` (that's read from the network), and a high-level type, such as a `case class`.
Second, we've got the generation of **schemas**, which describe the high-level types. Schemas are used for 
documentation (so that our endpoints are described in OpenAPI accurately), and for validation of incoming requests.

First, we need to pick a JSON library. There's a lot to choose from, but we'll go with [jsoniter](https://github.com/plokhotnyuk/jsoniter-scala),
which is the fastest JSON library for Scala. We'll need to add a dependency:

```scala
//> using dep com.softwaremill.sttp.tapir::tapir-jsoniter-scala:@VERSION@
```

This brings in both the `jsoniter-scala` library, as well as the `tapir` <-> `jsoniter-scala` integration. Once we
have that, let's define our data model, which we'll use for requests and responses. We'll define a single endpoint,
transforming a `Meal` class into a `Nutrition` one:

```scala
case class Meal(name: String, servings: Int, ingredients: List[String])
case class Nutrition(name: String, healthy: Boolean, calories: Int)
```

The first step is to define the functions, which will be able to serialise and deserialise these classes to JSON. This
can be done by hand, but most of the time we can rely on auto-derivation: a compile-time process which generates the
code, needed to transform a `String` into a `Meal` (or an error), and to transform a `Nutrition` into a `String`.

This is the task of our chosen JSON library. By adding a `derives ...` clause.
