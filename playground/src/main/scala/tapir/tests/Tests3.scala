package tapir.tests

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.Request
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import tapir._
import tapir.client.sttp._
import tapir.docs.openapi._
import tapir.server.akkahttp._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Tests3 extends App {

  case class X(x: String) extends AnyVal

  val e = endpoint.get
    .in("x" / path[String]("p1"))
    .mapInTo(X)
    .out(stringBody)

  val r: Route = e.toRoute((x: X) => Future.successful(Right(s"$x"): Either[Unit, String]))

  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  val server = Await.result(Http().bindAndHandle(r, "localhost", 8080), 1.minute)

  import com.softwaremill.sttp._
  implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = AkkaHttpBackend.usingActorSystem(actorSystem)

  val response3 = Await.result(e.toSttpRequest(uri"http://localhost:8080").apply(X("aa")).send(), 1.minute)
  println("RESPONSE: " + response3)

  Await.result(actorSystem.terminate(), 1.minute)
}
