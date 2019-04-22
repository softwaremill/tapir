# Debugging servers

When dealing with multiple endpoints, how to find out which endpoint handled a request, or why an endpoint didn't
handle it?

For this purpose, tapir provides optional logging. The logging options (and messages) can be customised by changing
the default `LoggingOptions` class, which is part of [server options](common.html).

The following can be logged:

1. `DEBUG`-log, when a request is handled by an endpoint
2. `DEBUG`-log, when the inputs can't be decoded, and the decode failure maps to a response
3. `DEBUG`-log, when the inputs can't be decoded, and the decode failure doesn't map to a response (the next endpoint 
   will be tried)
4. `ERROR`-log, when there's an exception during evaluation of the server logic

By default, logs of type (1), (2) and (4) are logged. Logging all decode failures (3) might be helpful when debugging,
but can also produce a large amount og logs.

Even if logging for a particular category (as described above) is set to `true`, normal logger rules apply - if you 
don't see the logs, please verify your logging levels for the appropriate packages.
