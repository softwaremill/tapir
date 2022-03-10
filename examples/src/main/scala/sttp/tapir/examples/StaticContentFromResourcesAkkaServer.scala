package sttp.tapir.examples

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.static.ResourcesOptions

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.io.StdIn

object StaticContentFromResourcesAkkaServer extends App {
  // we're pretending to be a SPA application, that is we serve index.html if the requested resource cannot be found
  val resourceEndpoints = resourcesGetServerEndpoint[Future](emptyInput)(
    StaticContentFromResourcesAkkaServer.getClass.getClassLoader,
    "webapp",
    ResourcesOptions.default.defaultResource(List("index.html"))
  )
  val route: Route = AkkaHttpServerInterpreter().toRoute(resourceEndpoints)

  // starting the server
  implicit val actorSystem: ActorSystem = ActorSystem()

  val bind = Http().newServerAt("localhost", 8080).bindFlow(route)
  Await.result(bind, 1.minute)
  println("Open: http://localhost:8080 and experiment with various paths.")
  println("Press any key to exit ...")
  StdIn.readLine()
  Await.result(actorSystem.terminate(), 1.minute)
}
