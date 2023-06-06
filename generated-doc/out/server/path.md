# Path matching

When a server receives a request, it must determine which endpoint might potentially handle it. In order to do so,
the endpoints are first pre-filtered, so that only endpoints where the path shape matches (that is, the number of
path inputs/segments must match, as well as any constant segments) are considered.

Next, the inputs are decoded, starting from the method. If the method inputs decode successfully, the path inputs 
are decoded. 

## Exact matches and trailing slashes

The path must match *exactly* - any remaining path segments will cause the endpoint not to match the request. 
However, extra trailing slashes are allowed. For example, `endpoint.in("api")` will match `/api`, `/api/`, but won't 
match `/`, `/api/users`.

To match only the root path, use an empty string: `endpoint.in("")` will match `http://server.com/` and
`http://server.com`.

## Matching any path

As with all other types of inputs, if no path input/path segments are defined, any path will match.

To match a path prefix, first define inputs which match the path prefix, and then capture any remaining part using
`paths`, e.g.: `endpoint.in("api" / "download").in(paths)`.

## Decoding failures

If decoding a path input fails, a `400 Bad Request` will be returned to the user. When using the default decode
failure handler, this can be customised to instead attempt decoding the next endpoint, by adding an attribute to the 
path input with `.onDecodeFailureNextEndpoint`.

Alternatively, another strategy can be implemented by using a completely custom decode failure handler. Both
topics are covered in more detail in the documentation of [error handling](errors.md).

## Endpoint ordering

The order in which endpoints are given to the server interpreter matters. If the shape of multiple endpoints matches 
certain requests, such endpoints should be listed from the most specific, to the least specific.

For example, an endpoint `endpoint.in("users" / "find")` is more specific than `endpoint.in("users" / path[Int]("id"))`,
and should be listed first: otherwise attempting to decode `"find"` as an integer will cause an error. More complex 
scenarios of path matching can be implemented using the approach described in the previous section.