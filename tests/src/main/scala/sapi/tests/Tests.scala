package sapi.tests

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import sapi._
import sapi.server.akkahttp._
import sapi.client.sttp._
import shapeless.HNil

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object Tests extends App {
  val sapi = Endpoint[Empty, HNil](None, None, EndpointInput.Multiple(Vector.empty))

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

  // sapi ?

  //  def json[T]: TypeMapper[T]
  //
  //  case class User(name: String, age: Int)
  //  implicit val userType: TypeMapper[User] = json[User]  // TODO.sample(User("x"))

  val path = "x" / pathCapture[String] / "z"

  val e = sapi
    .get()
    .in("x" / pathCapture[String] / "z" / pathCapture[Int]) // each endpoint must have a path and a method
    .in(query[String]("q1").and(query[Int]("q2")))

  // TODO
  //    .in(query[Int]("x"))
  //    .in(inputs)
  //    .in(header("Cookie"))
  //    .out[String]
  //    .outError[ProductError] // for 4xx status codes
  //    .name("xz") // name optional
  //    .description("...")

  val r: Route = e.toRoute((i: String, s: Int, p1: String, p2: Int) => Future.successful(s"$i $s $p1 $p2"))

  //

  // TEST

  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val server = Await.result(Http().bindAndHandle(r, "localhost", 8080), 1.minute)

  import com.softwaremill.sttp._
  implicit val backend = AkkaHttpBackend.usingActorSystem(actorSystem)

  val response = Await.result(sttp.get(uri"http://localhost:8080/x/aa/z/20?q1=x1&q2=91").send(), 1.minute)
  println("RESPONSE1: " + response)

//  type SttpReq[T] = Request[T, Nothing] // needed, unless implicit error
//  implicit val etc: EndpointToClient[SttpReq] = new EndpointToSttpClient
  val response2 = Await.result(e.toSttpClient.using("http://localhost:8080").apply("aa", 20, "x1", 91).send(), 1.minute)
  println("RESPONSE2: " + response2)

  Await.result(actorSystem.terminate(), 1.minute)
}
