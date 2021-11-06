# 10. Security refactoring

Date: 2021-11-03

## Context

Tapir's support for security-related features until version 0.18 is quite limited. There are `auth` inputs, which define
authentication credential metadata, used when generating documentation. There also is the possibility to define the
server logic either:

* for a fully defined endpoint, incrementally in parts 
* for a partially defined endpoint, for all inputs defined so far

However, this implementation has some drawbacks:

* all inputs are always decoded (including the body) before any logic is run, regardless whether the logic is defined 
  all at once, or in parts
* defining a partial endpoint with the authentication logic is possible, however the resulting type is complex
* it's confusing as to what's the difference between providing the server logic in parts for a fully defined endpoint,
  vs. defining a partial endpoint
* for cases where you don't want to create an endpoint with partial logic, it's not possible to enforce, or at least
  clearly communicate that some security logic should be run

## Decision

Security should be one of the central concepts for any HTTP library, so it is necessary to improve this area in tapir.
There were several design directions, which haven't been chosen:

* `DefferedServerEndpoint`, documented in the related [GitHub issue](https://github.com/softwaremill/tapir/issues/1167),
  which inverts the order in which the security & main logic are provided, comparing to the existing partial server 
  logic feature. This, however, doesn't really solve any of the issues.
* creating a security interceptor, which would run the given security logic for endpoints tagged as "secure"; the 
  computed "user" instance (if authentication is successful) would be placed in a server request attribute, and could 
  later be extracted using a server-only input. Apart from the necessity of adding server attributes, and enriching the 
  secure endpoints with an input but only when interpreted as a server, the main problem with this implementation was 
  deciding if the security logic should be run, given an endpoint. The security logic from the interceptor should run
  only if the endpoint "matches" the request - which means we need to decode the inputs; however, we don't want to 
  decode the body, which means that the interceptor would have to deeply interfere with the inner workings of the 
  `ServerInterpreter`. Possibly, a dedicated callback would be needed, making the whole process fragile and complex.
  Not to mention problems with passing the authentication credential metadata to generate documentation, or making 
  proper client calls.
* creating a parallel `SecureEndpoint[A, I, E, O, R]` class, which would have dedicated security-related inputs, next
  to `Endpoint[A, I, E, O, R]`. We would need a similar distinction for `ServerEndpoint`. However, converting a type
  that is central to tapir, `Endpoint`, from a case class to a sealed trait family would complicate the codebase
  and endpoint usage significantly.

Instead, we decided to extend the base `Endpoint` type with dedicated security inputs, adding a type parameter (which is 
an unfortunate, but necessary consequence). Similarly, `ServerEndpoint` gathered two new parameters: the type of the
security inputs, and the type of the values returned by a successful invocation of the security logic. This is a major
source-level breaking change, but to properly support security concerns, it had to be done.

For endpoints without security defined, a type alias is introduced - `PublicEndpoint`, which fixed the type of the 
security inputs to `Unit`. This corresponds to the old type of endpoints - hence for the migration period, the type
of endpoints can be changed to `PublicEndpoint` and everything should work the same.

When interpreting an endpoint as a server, first the basic inputs (that is path, query, headers, without the body) are 
decoded, to verify that the endpoint matches the request - this solves the major problem that we had with the 
interceptor approach. Only when the inputs decode successfully, first the security logic is run, then the body is 
decoded, and finally the main logic is run, given the value of the result of the security logic and the decoded inputs.

An endpoint with security inputs defined clearly communicates, that some form of security logic needs to be provided.
In fact, the only way to provide server logic for secure endpoints is a two-step process: first the security logic
using `.serverSecurityLogic`, then the main logic using `.serverLogic`. For public endpoints, it's possible to simply
call `.serverLogic`.

### Partial server endpoints

Partial endpoints can be defined in a similar way. After providing the security logic using `.serverSecurityLogic`, 
additional inputs and outputs can be defined. This way, a base "secure" endpoint can be defined, and later extended.
This approach also works seamlessly with client and documentation interpreters.

This two-stage approach of providing server logic is less flexible that the previous `.serverLogicForCurrent`, however
it more clearly defines the intent, and is less complicated when it comes to type-level computations on tuples. Hence,
it should be easier to understand.

Providing the server logic in parts is also somewhat implemented using the two-stage server logic approach. Similarly,
this is now limited to maximum two stages. If the main server logic is fragmented into multiple pieces, it's up to the
user to combine these functions into a single one.

### Auth inputs

Given that there's a dedicated section for security inputs, should we keep the distinct auth inputs (e.g. `auth.bearer`
or `auth.apiKey`)? It could seem that this is no longer necessary - after all, all inputs defined in the security
section can be assumed to provide authentication credentials.

However, there's still a need to provide meta-data for security-related inputs, such as naming security schemes for
OpenAPI, or providing appropriate `WWW-Authenticate` challenges. Hence, if users wish to provide this meta-data, some
kind of endpoint input wrapper is still needed.

Secondly, it's convenient having helper methods such as `auth.bearer`, where the `auth` objects groups all 
authentication-related inputs and provides them with the proper codecs and meta-data.

Finally, it is possible that users wish to capture more data as part of the security inputs, in addition to the
authentication credentials - such as fixed path segments, variable path segments, additional query parameters etc.

Hence, the authentication-credential inputs remain unchanged, at least for the time being.