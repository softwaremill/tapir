# Authentication

Inputs which carry authentication data wrap another input can be marked as such by declaring them using members of the
`auth` object. Apart from predefined codecs for some authentication methods, such inputs will be treated differently]
when generating documentation. Otherwise, they behave as normal inputs which map to the the given type.

Currently, the following authentication inputs are available (assuming `import sttp.tapir._`):

* `auth.apiKey(anotherInput)`: wraps any other input and designates it as an api key. The input is typically a header, 
cookie or a query parameter
* `auth.basic: EndpointInput[UsernamePassword]`: maps to the base64-encoded username/password pair in the 
`Authorization` header
* `auth.bearer: EndpointInput[String]`: maps to `Bearer [token]` in the `Authorization` header
* `auth.oauth2.authorizationCode(authorizationUrl, tokenUrl, scopes, refreshUrl): EndpointInput[String]`: creates an 
OAuth2 authorization using authorization code - sign in using an auth service (for documentation, requires defining also 
the  `oauth2-redirect.html`, see [Generating OpenAPI documentation](../openapi.html))

Multiple authentication inputs indicate that all of the given authentication values should be provided. Specifying
alternative authentication methods (where only one value out of many needs to be provided) is currently not supported.

When interpreting a route as a server, it is useful to define the authentication input first, to be able to share the
authentication logic among multiple endpoints easily. See [server logic](../server/logic.html) for more
details.

## Next

Read on about [datatypes integrations](integrations.html).
