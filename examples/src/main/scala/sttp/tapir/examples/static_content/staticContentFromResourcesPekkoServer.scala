// {cat=Static content; effects=Future; server=Pekko HTTP}: Serving static files from resources

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-files:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-pekko-http-server:1.11.8

package sttp.tapir.examples.static_content

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import sttp.tapir.*
import sttp.tapir.files.*
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.io.StdIn

@main def staticContentFromResourcesPekkoServer(): Unit =
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  // we're pretending to be a SPA application, that is we serve index.html if the requested resource cannot be found
  val resourceEndpoints = staticResourcesGetServerEndpoint[Future](emptyInput)(
    this.getClass.getClassLoader,
    "webapp",
    FilesOptions.default.defaultFile(List("index.html"))
  )
  val route: Route = PekkoHttpServerInterpreter().toRoute(resourceEndpoints)

  // starting the server
  val bind = Http().newServerAt("localhost", 8080).bindFlow(route)
  Await.result(bind, 1.minute)
  println("Open: http://localhost:8080 and experiment with various paths.")
  println("Press any key to exit ...")
  StdIn.readLine()
  Await.result(actorSystem.terminate(), 1.minute)
