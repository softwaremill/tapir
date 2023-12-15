package sttp.tapir.examples2.static_content

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import sttp.tapir._
import sttp.tapir.files._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.io.StdIn

object StaticContentFromResourcesAkkaServer extends App {
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  // we're pretending to be a SPA application, that is we serve index.html if the requested resource cannot be found
  val resourceEndpoints = staticResourcesGetServerEndpoint[Future](emptyInput)(
    StaticContentFromResourcesAkkaServer.getClass.getClassLoader,
    "webapp",
    FilesOptions.default.defaultFile(List("index.html"))
  )
  val route: Route = AkkaHttpServerInterpreter().toRoute(resourceEndpoints)

  // starting the server
  val bind = Http().newServerAt("localhost", 8080).bindFlow(route)
  Await.result(bind, 1.minute)
  println("Open: http://localhost:8080 and experiment with various paths.")
  println("Press any key to exit ...")
  StdIn.readLine()
  Await.result(actorSystem.terminate(), 1.minute)
}
