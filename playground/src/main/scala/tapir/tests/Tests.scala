package tapir.tests

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import tapir._
import tapir.server.akkahttp._
import tapir.client.sttp._
import tapir.docs.openapi._
import tapir.openapi.Info

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object Tests extends App {
  // TODO
  //  case class User()
  //
  //  implicit val userType = json[User].sample(User("x"))
  //  implicit val ? = string.format(base64)
  //  implicit val productErrorType = json[ProductError]
  //
  //  val qp = query[String]("v").description("p")      // : EndpointInput[HList] - each input extracts 0+ data from the request
  //  // an input can be created from a function RequestContext => T
  //  val hp = header("X-Y")
  //  val bp = body[User]
  //
  //  // verify that there's only one body + that query params aren't repeated?
  //  val inputs = qp.and(hp).and(bp) // andUsing(combineFn)

  val p = "x" / path[String]("p1") / "z"

  val e: Endpoint[(String, Int, String, Int, Option[String], Int), Unit, (String, Int), Nothing] = endpoint.get
    .in("x" / path[String]("p1") / "z" / path[Int]("p2")) // each endpoint must have a path and a method
    .in(query[String]("q1").description("A q1").and(query[Int]("q2").example(99)))
    .in(query[Option[String]]("q3"))
    .in(header[Int]("zzz"))
    .out(stringBody)
    .out(header[Int]("yyy"))

  val r: Route = e.toRoute(
    (i: String, s: Int, p1: String, p2: Int, p3: Option[String], h1: Int) =>
      //Future.successful(Right((s"$i $s $p1 $p2${p3.map(" " + _).getOrElse("")} $h1", 192))))
      Future.successful(Right((s"$i $s $p1 $p2${p3.map(" " + _).getOrElse("")} $h1", 192)): Either[Unit, (String, Int)]))

  //

  import tapir.openapi.circe.yaml._

  val docs = e.toOpenAPI(Info("Example 1", "1.0"))
  println("XXX")
  println(docs.toYaml)
  println("YYY")

  // TEST

  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  val server = Await.result(Http().bindAndHandle(r, "localhost", 8080), 1.minute)

  import com.softwaremill.sttp._
  implicit val backend: SttpBackend[Future, Source[ByteString, Any]] = AkkaHttpBackend.usingActorSystem(actorSystem)

  val response1 = Await.result(sttp.get(uri"http://localhost:8080/x/aa/z/20?q1=x1&q2=91").header("zzz", "44").send(), 1.minute)
  println("RESPONSE1: " + response1)

  val response2 = Await.result(sttp.get(uri"http://localhost:8080/x/aa/z/20?q1=x1&q2=91&q3=kkk").header("zzz", "44").send(), 1.minute)
  println("RESPONSE2: " + response2)

//  type SttpReq[T] = Request[T, Nothing] // needed, unless implicit error
//  implicit val etc: EndpointToClient[SttpReq] = new EndpointToSttpClient
  val response3 = Await.result(e.toSttpRequest(uri"http://localhost:8080").apply("aa", 20, "x1", 91, None, 44).send(), 1.minute)
  println("RESPONSE3: " + response3)

  val response4 = Await.result(e.toSttpRequest(uri"http://localhost:8080").apply("aa", 20, "x1", 91, Some("kkk"), 44).send(), 1.minute)
  println("RESPONSE4: " + response4)

  Await.result(actorSystem.terminate(), 1.minute)
}
