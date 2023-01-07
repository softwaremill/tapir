# Logging & debugging

When dealing with multiple endpoints, how to find out which endpoint handled a request, or why an endpoint didn't
handle a request?

For this purpose, tapir provides optional logging. The logging options (and messages) can be customised by providing
an instance of the `ServerLog` trait, which is part of [server options](options.md).

An instance of the default implementation, `DefaultServerLog`, is available in the companion object for the 
interpreter's options class, e.g. `Http4sServerOptions.defaultServerLog` or `NettyZioServerOptions.defaultServerLog`.

This instance can be customised using the following flags:

1. `logWhenReceived`: log when a request is first received (default: `false`, `DEBUG` log)
2. `logWhenHandled`: log when a request is handled by an endpoint, or when the inputs can't be decoded, and the decode 
   failure maps to a response (default: `true`, `DEBUG` log)
3. `logAllDecodeFailures`: log each time when the inputs can't be decoded, and the decode failure doesn't map to a 
   response (the next endpoint will be tried; default: `false`, `DEBUG` log)
4. `logLogicExceptions`: log when there's an exception during evaluation of the server logic  (default: `true`, 
   `ERROR` log)

Logging all decode failures (3) might be helpful when debugging, but can also produce a large amount of logs, hence
it's disabled by default.

Even if logging for a particular category (as described above) is set to `true`, normal logger rules apply - if you 
don't see the logs, please verify your logging levels for the appropriate packages.
