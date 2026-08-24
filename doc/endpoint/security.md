# Security

Endpoints can have dedicated security-related inputs. The type of those inputs is captured as the first type parameter,
`A`, of the `Endpoint` type. Security inputs can be added by `.securityIn` methods, and behave the same as regular 
inputs.

The security inputs play a crucial role when defining the [server logic](../server/logic.md) for an endpoint. They also
aim to clearly communicate which part of the endpoint's input is security-related. Finally, they are part of a mechanism
for building reusable base "secure" endpoints, either with the security logic provided or not, which can later be 
extended by other endpoints.

## Authentication inputs

Inputs which map to authentication credentials can be created using methods available in the `auth` object. Such
inputs in addition to the base input (such as an `Authorization` header or a cookie), contain security-related metadata, 
for example the name of the security scheme that should be used for documentation.

```{note}
Note that security inputs added using `.securityIn` can contain both dedicated auth credentials inputs created
using one of the methods from `auth`, and arbitrary "regular" inputs, such as path components. Similarly, regular 
inputs can contain inputs created through `auth`, though typically this shouldn't be the case.
```

Currently, the following authentication inputs are available (assuming `import sttp.tapir.*`):

* `auth.apiKey(anotherInput)`: wraps any other input and designates it as an api key. The input is typically a header, 
cookie or a query parameter
* `auth.basic[T]`: reads data from the `Authorization` header, removing the `Basic ` prefix. To parse the data as a 
base64-encoded username/password combination, use: `basic[UsernamePassword]`.
* `auth.bearer[T]`: reads data from the `Authorization` header, removing the `Bearer ` prefix. To get the token
as a string, use: `bearer[String]`.
* `auth.oauth2.authorizationCodeFlow(authorizationUrl, scopes, tokenUrl, refreshUrl): EndpointInput[String]`: creates an 
OAuth2 authorization using authorization code - sign in using an auth service (for documentation, requires defining also 
the `oauth2-redirect.html`, see [Generating OpenAPI documentation](../docs/openapi.md)). Other OAuth2 flows are also
supported, as well as optional variants: `authorizationCodeFlow[Optional]`, `clientCredentialsFlow[Optional]`, 
`implicitFlow[Optional]`.

## Using the request body in security logic

Security logic sometimes needs the request body itself - for example, to verify a signature computed over the raw
payload. The request body can normally be read only once, so an endpoint which needs it in both
`serverSecurityLogic` and the main logic must mark one of the two declarations with `extractBodyFromRequest`:

```scala mdoc:compile-only
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import io.circe.generic.auto.*

case class Person(name: String, age: Int)

val secureEndpoint = endpoint.post
  .securityIn(auth.bearer[String]())
  .securityIn(extractBodyFromRequest(stringBody))
  .in("people")
  .in(jsonBody[Person])
```

An extracted body is still decoded on the server, using its own codec, but it isn't part of the endpoint's API
contract: there's only one request body on the wire, so the extracted declaration is excluded from the generated
documentation, and ignored by client interpreters. The unmarked body - `jsonBody[Person]` above - is the one that's
documented, and the one clients actually send.

Only bodies which can be re-read from buffered bytes can be extracted: string, byte array, byte buffer, input stream
and input stream range bodies. File and multipart bodies aren't accepted - `extractBodyFromRequest` requires a
`ReplayableRawBody` instance for the body's raw type, and none is provided for `RawBodyType.FileBody` or
`RawBodyType.MultipartBody` - while streaming and `oneOf` bodies aren't `EndpointIO.Body` values at all, so they
can't be passed to `extractBodyFromRequest` in the first place. Either way, the restriction is enforced at compile
time.

Note that a *single* body input needs no wrapper: an endpoint which reads the body only in `serverSecurityLogic`,
with no body declared in `in`, reads the request exactly once. It works without `extractBodyFromRequest`, and stays
fully documented and visible to clients.

## Authentication challenges

For each `auth` scheme, one can define `WWW-Authenticate` headers that should be returned by the server in case input is 
not provided. Default behavior is to return `401` status with the headers needed for authentication. To return different
status codes when authentication is missing, the decode failure handler can [be customised](../server/errors.md).

For example, if you define `endpoint.get.securityIn("path").securityIn(auth.basic[UsernamePassword]())` then the browser
will show you a password prompt.

## Grouping authentication inputs in docs

Optional and multiple authentication inputs have some additional rules as to how hey map to documentation, see the
["Authentication inputs and security requirements"](../docs/openapi.md) section in the OpenAPI docs for details.

## Limiting request body length

*Unsupported backends*: 
This feature is available for all server backends *except*: `akka-grpc`, `Armeria`, `Finatra`, `Helidon Nima`, `pekko-grpc`. 

Individual endpoints can be annotated with content length limit:

```scala mdoc:compile-only
import sttp.tapir.*
import sttp.tapir.server.model.EndpointExtensions.*

val limitedEndpoint = endpoint.maxRequestBodyLength(maxBytes = 16384L)
```

The `EndpointsExtensions` utility is available in `tapir-server` core module. 
Such protection prevents loading all the input data if it exceeds the limit. Instead, it will result  in a `HTTP 413` 
response to the client. 
Please note that in case of endpoints with `streamBody` input type, the server logic receives a reference to a lazily
evaluated stream, so actual length verification will happen only when the logic performs streams processing, not earlier.

## Next

Read on about [streaming support](streaming.md).
