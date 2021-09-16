# Serving static content

Tapir contains predefined endpoints, server logic and server endpoints which allow serving static content, originating
from local files or application resources. These endpoints respect etags as well as if-modified-since headers.

## Files

The easiest way to expose static content from the local filesystem is to use the `filesServerEndpoint`. This method
is parametrised with the path, at which the content should be exposed, as well as the local system path, from which
to read the data.

Such an endpoint has to be interpreted using your server interpreter. For example, using the akka-http interpreter:

```scala
import akka.http.scaladsl.server.Route

import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.Future

val filesRoute: Route = AkkaHttpServerInterpreter().toRoute(
  filesServerEndpoint[Future]("site" / "static")("/home/static/data")
)
```

Using the above endpoint, a request to `/site/static/css/styles.css` will try to read the 
`/home/static/data/css/styles.css` file.

A single file can be exposed using `fileServerEndpoint`.

## Resources

Similarly, the `resourcesServerEndpoint` can be used to expose the application's resources at the given prefix.

A single resource can be exposed using `resourceServerEndpoint`.

## Endpoint description and server logic

The descriptions of endpoints which should serve static data, and the server logic which implements the actual 
file/resource reading are also available separately for further customisation.

The `filesEndpoint` and `resourcesEndpoint` are descriptions which contain the metadata (including caching headers) 
required to serve a file or resource, and possible error outcomes. This is captured using the `StaticInput`, 
`StaticErrorOuput` and `StaticOutput[T]` classes.

The `sttp.tapir.static.Files` and `sttp.tapir.static.Resources` objects contain the logic implementing server-side
reading of files or resources, with etag/last modification support.