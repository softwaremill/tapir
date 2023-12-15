package sttp.tapir.examples2.multipart

import java.io.PrintWriter

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import sttp.client3._
import sttp.tapir.generic.auto._
import sttp.model.Part
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object MultipartFormUploadAkkaServer extends App {
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

  // the class representing the multipart data
  //
  // parts can be referenced directly; if part metadata is needed, we define the type wrapped with Part[_].
  //
  // note that for binary parts need to be buffered either in-memory or in the filesystem anyway (the whole request
  // has to be read to find out what are the parts), so handling multipart requests in a purely streaming fashion is
  // not possible
  case class UserProfile(name: String, hobby: Option[String], age: Int, photo: Part[TapirFile])

  // corresponds to: POST /user/profile [multipart form data with fields name, hobby, age, photo]
  val setProfile: PublicEndpoint[UserProfile, Unit, String, Any] =
    endpoint.post.in("user" / "profile").in(multipartBody[UserProfile]).out(stringBody)

  // converting an endpoint to a route (providing server-side logic); extension method comes from imported packages
  val setProfileRoute: Route = AkkaHttpServerInterpreter().toRoute(setProfile.serverLogicSuccess { data =>
    Future {
      val response = s"Received: ${data.name} / ${data.hobby} / ${data.age} / ${data.photo.fileName} (${data.photo.body.length()})"
      data.photo.body.delete()
      response
    }
  })

  // starting the server
  val bindAndCheck = Http().newServerAt("localhost", 8080).bindFlow(setProfileRoute).map { _ =>
    val testFile = java.io.File.createTempFile("user-123", ".jpg")
    val pw = new PrintWriter(testFile); pw.write("This is not a photo"); pw.close()

    // testing
    val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
    val result: String = basicRequest
      .response(asStringAlways)
      .get(uri"http://localhost:8080/user/profile")
      .multipartBody(multipart("name", "Frodo"), multipart("hobby", "hiking"), multipart("age", "33"), multipartFile("photo", testFile))
      .send(backend)
      .body
    println("Got result: " + result)

    assert(result == s"Received: Frodo / Some(hiking) / 33 / Some(${testFile.getName}) (19)")
  }

  Await.result(bindAndCheck.transformWith { r => actorSystem.terminate().transform(_ => r) }, 1.minute)
}
