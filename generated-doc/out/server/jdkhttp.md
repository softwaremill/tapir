# Running as a JDK http server

To expose endpoints using the 
[http server built into the JDK](https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/package-summary.html)
(`com.sun.net.httpserver`), first add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-jdkhttp-server" % "1.11.8"
```

Then, import the package:

```scala
import sttp.tapir.server.jdkhttp.*
```

and use `JdkHttpServer().addEndpoints` to expose server endpoints.

These methods require a single, or a list of `ServerEndpoint`s, which can be created by adding [server logic](logic.md)
to an endpoint. You can use shortcut extension methods exposed by the package to transform your `Endpoint`s into 
`ServerEndpoint`s by calling `.handle` method family on them (an equivalent to `.serverLogic` family, but with the effect
type fixed, which simplifies type inference). The `handle` naming convention was introduced to avoid conflicts with the 
original `serverLogic` methods and also because names are shorter.

For example:

```scala
import sttp.tapir.*
import sttp.tapir.server.jdkhttp.*

val helloWorld = endpoint
  .get
  .in("hello").in(query[String]("name"))
  .out(stringBody)
  .handle(name => Right(s"Hello, $name!"))

val server: HttpServer = 
  JdkHttpServer().addEndpoint(helloWorld).start()
```

## Important notice:

**This server runs on a single worker thread by default.** This is ok for testing, toy projects and things that never see any load. 
If you want it to scale please read about the `executor` configuration option below and set it accordingly. 

Given the `com.sun.net.httpserver` package is standardised and a part of public JDK API since JDK 18 (JEP 408) this server can be 
considered stable.

## Configuration

The interpreter can be configured by providing an `JdkHttpServerOptions` value, see [server options](options.md) for
details.

Most options can be configured directly using a `JdkHttpServer` instance, such as the host and port. Possible options are:

* `send404WhenRequestNotHandled`:
  Should a 404 response be sent, when the request hasn't been handled by defined endpoints. This is a safe default, but if there are multiple handlers for 
  the same context path, this should be set to `false`. In that case, you can verify if the request has been handled using
  `JdkHttpServerInterpreter.isRequestHandled`.

* `basePath`:
  Path under which endpoints will be mounted when mounted on JdkHttpServer instance. I.e.: basePath of `/api` and endpoint `/hello` will
  result with a real path of `/api/hello`.

* `port`: IP port to which JdkHttpServer instance will be bound. Default is `0`, which means any random port provided by the OS.

* `host`: 
  Hostname or IP address (ie.: `localhost`, `0.0.0.0`) to which JdkHttpServer instance will be bound. Default is `0.0.0.0` which binds the
  server to all network interfaces available on the OS.

* `executor`:
  Allows you to configure the `Executor` which will be used to handle HTTP requests. By default `com.sun.net.httpserver.HttpServer` uses a
  single thread (a calling thread executor to be precise) executor to handle traffic which might be fine for local toy projects.

  If you intend to use this HTTP server for any deployments that will run under load it's absolutely necessary to set an executor that
  will use proper thread pool to handle the load. Recommended approach is to use `JdkHttpServerOptions.httpExecutor` method to create a
  ThreadPoolExecutor that will scale under load. You can also use an Executor returned from any of the constructors in the
  `java.util.concurrent.Executors` class.

  Alternatively, if running with a JDK 19+ you can leverage Project Loom and use `Executors.newVirtualThreadPerTaskExecutor()` to run
  each request on a virtual thread. This however means it is possible for your server to be overloaded with work as each request will be
  given a thread with no backpressure on how many should be executed in parallel.

* `httpsConfigurator`:
  Optional HTTPS configuration. Takes an instance of `com.sun.net.httpserver.HttpsConfigurator`, which is a thin wrapper around
  `javax.net.ssl.SSLContext` to configure the SSL termination for this server.

* `backlogSize`:
  Sets the size of server's tcp connection backlog. This is the maximum number of queued incoming connections to allow on the listening
  socket. Queued TCP connections exceeding this limit may be rejected by the TCP implementation. If set to 0 or less the system default
  for backlog size will be used. Default is 0.

