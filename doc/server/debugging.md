# Debugging

When dealing with multiple endpoints, how to find out which endpoint handled a request, or why an endpoint didn't
handle a request?

For this purpose, tapir provides optional logging. The logging options (and messages) can be customised by changing
the default `LogRequestHandling` class, which is part of [server options](options.html).

The following can be customised:

1. `logWhenHandled`: when a request is handled by an endpoint, or when the inputs can't be decoded, and the decode 
   failure maps to a response (default: `true`, `DEBUG` log)
2. `logAllDecodeFailures`: when the inputs can't be decoded, and the decode failure doesn't map to a response (the next 
   endpoint will be tried; default: `false`, `DEBUG` log)
3. `logLogicExceptions`: when there's an exception during evaluation of the server logic  (default: `true`, `ERROR` log)

Logging all decode failures (2) might be helpful when debugging, but can also produce a large amount of logs, hence
it's disabled by default.

Even if logging for a particular category (as described above) is set to `true`, normal logger rules apply - if you 
don't see the logs, please verify your logging levels for the appropriate packages.
