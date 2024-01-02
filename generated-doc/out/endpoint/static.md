# Serving static content

Tapir contains predefined endpoints, server logic and server endpoints which allow serving static content, originating
from local files or application resources. These endpoints respect etags, byte ranges as well as if-modified-since headers.

```eval_rst
.. note::
  Since Tapir 1.3.0, static content is supported via the new `tapir-files` module. If you're looking for
  the API documentation of the old static content API, switch documentation to an older version.
```

In order to use static content endpoints, add the module to your dependencies:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-files" % "1.9.6"
```

## Files

The easiest way to expose static content from the local filesystem is to use the `staticFilesServerEndpoint`. This method
is parametrised with the path, at which the content should be exposed, as well as the local system path, from which
to read the data.

Such an endpoint has to be interpreted using your server interpreter. For example, using the [akka-http](../server/akkahttp.md) interpreter:

```scala
import akka.http.scaladsl.server.Route

import sttp.tapir._
import sttp.tapir.files._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val filesRoute: Route = AkkaHttpServerInterpreter().toRoute(
  staticFilesGetServerEndpoint[Future]("site" / "static")("/home/static/data")
)
```

Using the above endpoint, a request to `/site/static/css/styles.css` will try to read the
`/home/static/data/css/styles.css` file.

To expose files without a prefix, use `emptyInput`. For example, using the [netty](../server/netty.md) interpreter, the
below exposes the content of `/var/www` at `http://localhost:8080`:

```scala
import sttp.tapir.server.netty.NettyFutureServer
import sttp.tapir.emptyInput
import sttp.tapir._
import sttp.tapir.files._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

NettyFutureServer()
  .port(8080)
  .addEndpoint(staticFilesGetServerEndpoint[Future](emptyInput)("/var/www"))
  .start()
  .flatMap(_ => Future.never)
```

A single file can be exposed using `staticFileGetServerEndpoint`.
Similarly, you can expose HEAD endpoints with `staticFileHeadServerEndpoint` and `staticFilesHeadServerEndpoint`.
If you want to serve both GET and HEAD, use `staticFilesServerEndpoints`.

The file server endpoints can be secured using `ServerLogic.prependSecurity`, see [server logic](../server/logic.md)
for details.

## Resources

Similarly, the `staticResourcesGetServerEndpoint` can be used to expose the application's resources at the given prefix.

A single resource can be exposed using `staticResourceGetServerEndpoint`.

## FileOptions

Endpoint constructor methods for files and resources can receive optional `FileOptions`, which allow to configure additional settings:

```scala
import sttp.model.headers.ETag
import sttp.tapir.emptyInput
import sttp.tapir._
import sttp.tapir.files._

import scala.concurrent.Future

import java.net.URL

val customETag: Option[RangeValue] => URL => Future[Option[ETag]] = ???
val customFileFilter: List[String] => Boolean = ???

val options: FilesOptions[Future] =
  FilesOptions
    .default
    // serves file.txt.gz instead of file.txt if available and Accept-Encoding contains "gzip"
    .withUseGzippedIfAvailable
    .calculateETag(customETag)
    .fileFilter(customFileFilter)
    .defaultFile(List("default.md"))

val endpoint = staticFilesGetServerEndpoint(emptyInput)("/var/www", options)
```

## Endpoint description and server logic

The descriptions of endpoints which should serve static data, and the server logic which implements the actual
file/resource reading are also available separately for further customisation.

The `staticFilesGetEndpoint` and `staticResourcesGetEndpoint` are descriptions which contain the metadata (including caching headers)
required to serve a file or resource, and possible error outcomes. This is captured using the `StaticInput`,
`StaticErrorOuput` and `StaticOutput[T]` classes.

The `sttp.tapir.files.Files` and `sttp.tapir.files.Resources` objects contain the logic implementing server-side
reading of files or resources, with etag/last modification support.

## WebJars

The content of [WebJars](https://www.webjars.org) that are available on the classpath can be exposed using the
following routes (here using the `/resources` context path):

```scala
import sttp.tapir._
import sttp.tapir.files._

import scala.concurrent.Future

val webJarRoutes = staticResourcesGetServerEndpoint[Future]("resources")(
  this.getClass.getClassLoader, "META-INF/resources/webjars")
```
