# 6. Error variants

```{note}
The tutorial is also available [as a video](https://www.youtube.com/watch?v=w2ZL3WvhBZ8).
```

Quite often, there's more than one thing that might go wrong. On the other hand, success can also have many facets.

In the previous tutorials we've seen that Tapir includes built-in support for differentiating between successful and
error outputs. That's because in most cases the response that is returned in case of an error is totally different
from a response returned in case of success.

Hence, Tapir has built-in, top-level response variants: either error, or success. It's also possible to introduce
more response variants, on lower levels, which further differentiate error and success scenarios.

## `oneOf` outputs

Such differentiation of both success and error output can be achieved using `oneOf` output descriptions. As the name 
suggests, such outputs describe responses, which can take the shape of one of the given variants. Each variant is a 
description of an output, such as the ones that we've seen so far. 

We've also seen that each output describes a mapping between a high-level Scala type and the HTTP response.
The same is true for `oneOf` outputs. Because `oneOf` has variants, we need a high-level type which also has variants. 
Each variant of the Scala type will correspond to one output variant.

```{note}
For error and successful outputs we also have variants in the high-level type, `Either[E, O]`. There are two variants: 
`Left` and `Right`, corresponding to error and success outputs.
```

To represent various output variants on the Scala-value level, we'll typically use an `enum`. Each enum has a number of 
variants: exactly what we need. Mind that using an enum is not required when using `oneOf` outputs, just convenient.

## High-level response representation

Let's start coding! We'll try to describe an endpoint, which fetches the avatar of the user. Here's a list of things
that might go wrong:

* unauthorized, in case the avatar of the requested user is not public
* not found, in case there's no user with the provided id
* other, in case the server logic would like to respond with a generic error

And there's also a "list of things that might go right", meaning success variants:

* found, with an array of bytes, containing the avatar
* redirect, with an address where the avatar is located

We'll represent both of these as an enum. We'll be editing the `variants.scala` file:

```scala
enum AvatarError:
  case Unauthorized
  case NotFound
  case Other(msg: String)

enum AvatarSuccess:
  case Found(bytes: Array[Byte])
  case Redirect(location: String)
```

## An output for a single variant

Now that we have the high-level model in place, let's describe an output for a single variant; `AvatarSuccess.Redirect`
is the most complicated one (we won't be using `oneOf` just yet!).

In case of a redirect, we want the response to contain:

* the `307 Temporary Redirect` status code
* the `Location` header, with a value pointing to the avatar's location

Endpoint outputs are described as instances of the `EndpointOutput` type. We've already seen output descriptions in 
previous tutorials; `stringBody` is an `EndpointOutput[String]`, and `jsonBody[Nutrition]` is an 
`EndpointOutput[Nutrition]`. Similarly, here, our goal is to obtain a value of type 
`EndpointOutput[AvatarSuccess.Redirect]`, which will be mapped to the status code & header described above.

For the status code, we can use the constant status code output: `statusCode(StatusCode.TemporaryRedirect)`. It takes a
`StatusCode` instance from the `sttp.model` package, and has the type `EndpointOutput[Unit]`. The `Unit` means that
it doesn't map any high-level values to the response: it's a constant, and it always describes the same 307 status code.

For the header, we have the `header[String](HeaderNames.Location)` output. Just as with query and path parameters that
we've seen before, the `String` type parameter specifies that we'd like to serialize the header from a string. We can't
request serializing `AvatarSuccess.Redirect` instances, as Tapir knows nothing about that type. Hence, here we'll have 
an `EndpointOutput[String]`: 

```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8

import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir.*

enum AvatarSuccess:
  case Found(bytes: Array[Byte])
  case Redirect(location: String)

val o1: EndpointOutput[Unit] = statusCode(StatusCode.TemporaryRedirect)
val o2: EndpointOutput[String] = header[String](HeaderNames.Location)
```

We can combine these outputs into a composite output using the `EndpointOutput.and` method. This is similar to adding 
multiple outputs to an endpoint description using multiple `Endpoint.out` invocations. In fact, `Endpoint.out` 
internally using `EndpointOutput.and` to combine the endpoints defined so far.

The type of the composite output corresponds to the values, that are mapped to the response. As `o1` doesn't map any
values (it's a constant), the composite output will also have the type `EndpintOutput[String]`. Finally, we can map
this output to the `AvatarSuccess.Redirect` type using `.mapTo`, which we've learned about last time:

{emphasize-lines="12-13"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8

import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir.*

enum AvatarSuccess:
  case Found(bytes: Array[Byte])
  case Redirect(location: String)

val o1: EndpointOutput[Unit] = statusCode(StatusCode.TemporaryRedirect)
val o2: EndpointOutput[String] = header[String](HeaderNames.Location)
val o3: EndpointOutput[String] = o1.and(o2)
val o3mapped: EndpointOutput[AvatarSuccess.Redirect] = o3.mapTo[AvatarSuccess.Redirect]
```

## Picking the right variant

We're almost ready to define the `oneOf` output with variants. Each variant consists of two parts: the output, and a 
function determining (at run-time) if the variant should be used for a given high-level type. That is, when server logic
returns an instance of the high-level type, we need to determine, which variant should be used to map it to the
HTTP response.

The default way to create variants is using the `oneOfVariant(EndpointOutput[T])` method. It creates a description of
a variant, which will match all instances of the `T` type. This check is done by inspecting the run-time class of the
`T` instance. This often works, but not always, as full type information is not always available at run-time, e.g. if 
`T` is a generic type. If that's the case, you'll get a compile-time error.

However, for `AvatarSuccess`, this default way of creating variants works just fine, as we are dealing with enum cases, 
each of which translates to a separate class. Our one-of successful output takes the following form:

{emphasize-lines="13-16"}
```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8

import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir.*

enum AvatarSuccess:
  case Found(bytes: Array[Byte])
  case Redirect(location: String)

val o1: EndpointOutput[Unit] = statusCode(StatusCode.TemporaryRedirect)
val o2: EndpointOutput[String] = header[String](HeaderNames.Location)

val successOutput: EndpointOutput[AvatarSuccess] = oneOf(
  oneOfVariant(o1.and(o2).mapTo[AvatarSuccess.Redirect]),
  oneOfVariant(byteArrayBody.mapTo[AvatarSuccess.Found])
)
```

The `oneOf` output can be typed using the common parent of both variants, which is `AvatarSuccess`. The server logic
will then have to return an instance of `AvatarSuccess`, in case of successful completion.

```{warn}
Unfortunately, Tapir is not able to verify at compile-time that the variants are exhaustive, that is that every variant
of the high-level type has a corresponding output-variant.
```

## Dealing with singleton enum cases

The output for `AvatarError` can be created similarly, with one caveat. It has two no-parameter cases (`Unauthorized` 
and `NotFound`), which are not translated into separate classes by the compiler. Hence, the run-time checks done by 
`oneOfVariant` would fail, or more precisely, any no-parameter case would be determined to match the first 
no-parameter-case output variant, yielding incorrect responses.

To fix this, we can use the `oneOfVariantSingletonMatcher` method. It takes a unit-typed output, along with an exact 
value, to which the high-level output must be equal, for the variant to be chosen:

```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8

import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir.*

enum AvatarError:
  case Unauthorized
  case NotFound
  case Other(msg: String)

val errorOutput: EndpointOutput[AvatarError] = oneOf(
  oneOfVariantSingletonMatcher(statusCode(StatusCode.Unauthorized))(AvatarError.Unauthorized),
  oneOfVariantSingletonMatcher(statusCode(StatusCode.NotFound))(AvatarError.NotFound),
  oneOfVariant(stringBody.mapTo[AvatarError.Other])
)
```

## Describing the entire endpoint

Equipped with `oneOf` outputs, we can now fully describe and test our endpoint:

```scala
//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.8

import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.shared.Identity

enum AvatarError:
  case Unauthorized
  case NotFound
  case Other(msg: String)

enum AvatarSuccess:
  case Found(bytes: Array[Byte])
  case Redirect(location: String)

val o1: EndpointOutput[Unit] = statusCode(StatusCode.TemporaryRedirect)
val o2: EndpointOutput[String] = header[String](HeaderNames.Location)

val successOutput: EndpointOutput[AvatarSuccess] = oneOf(
  oneOfVariant(o1.and(o2).mapTo[AvatarSuccess.Redirect]),
  oneOfVariant(byteArrayBody.mapTo[AvatarSuccess.Found])
)

val errorOutput: EndpointOutput[AvatarError] = oneOf(
  oneOfVariantSingletonMatcher(statusCode(StatusCode.Unauthorized))(AvatarError.Unauthorized),
  oneOfVariantSingletonMatcher(statusCode(StatusCode.NotFound))(AvatarError.NotFound),
  oneOfVariant(stringBody.mapTo[AvatarError.Other])
)

@main def tapirErrorVariants(): Unit =
  val avatarEndpoint = endpoint.get
    .in("user" / "avatar")
    .in(query[Int]("id"))
    .out(successOutput)
    .errorOut(errorOutput)
    // Int => Either[AvatarError, AvatarSuccess]
    .handle {
      case 1 => Right(AvatarSuccess.Found(":-)".getBytes))
      case 2 => Right(AvatarSuccess.Redirect("https://example.org/me.jpg"))
      case 3 => Left(AvatarError.Unauthorized)
      case 4 => Left(AvatarError.Other("We don't like this user."))
      case _ => Left(AvatarError.NotFound)
    }

  val swaggerEndpoints = SwaggerInterpreter().fromServerEndpoints[Identity](
    List(avatarEndpoint), "My App", "1.0")

  NettySyncServer()
    .port(8080)
    .addEndpoint(avatarEndpoint)
    .addEndpoints(swaggerEndpoints)
    .startAndWait()
```

As you can see, the server logic needs to return either an `AvatarError`, or a `AvatarSuccess`. This corresponds to the 
outputs that we have defined.

Let's run a couple of tests:

```bash
# first console
% scala-cli variants.scala

# second console
% curl -v "http://localhost:8080/user/avatar?id=2"
< HTTP/1.1 307 Temporary Redirect
< server: tapir/1.10.10
< Location: https://example.org/me.jpg

% curl -v "http://localhost:8080/user/avatar?id=7" 
< HTTP/1.1 404 Not Found

% curl -v "http://localhost:8080/user/avatar?id=3" 
< HTTP/1.1 401 Unauthorized
```

We're also generating documentation. If you take a look at the [`http://localhost:8080/docs`](http://localhost:8080/docs), 
you'll see that each status code is properly documented.

## Further reading

There's more ways to define `oneOf` variants, these are described in more detail on the reference page: 
[](../endpoint/oneof.md).
