package sttp.tapir.server.http4s

import cats.syntax.semigroupk._
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{Method, Request, Status}
import sttp.tapir._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.http4s.implicits._

class Http4sRoutesTest extends AnyWordSpec with Matchers {
  private implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  "Routes" should {
    "match the same path with different methods" in {
      val getEndpoint = endpoint.get.in("api" / "data").out(plainBody[String]).serverLogic[IO](_ => IO(Right("getData")))
      val postEndpoint = endpoint.post.in("api" / "data").out(plainBody[String]).serverLogic[IO](_ => IO(Right("postData")))

      val getRoutes = Http4sServerInterpreter[IO]().toRoutes(getEndpoint)
      val postRoutes = Http4sServerInterpreter[IO]().toRoutes(postEndpoint)

      val allRoutes = getRoutes <+> postRoutes
      val service = allRoutes.orNotFound

      val getReq = Request[IO](method = Method.GET, uri = uri"/api/data")
      val postReq = Request[IO](method = Method.POST, uri = uri"/api/data")

      (for {
      getResp  <- service.run(getReq)
      postResp <- service.run(postReq)
      getData  <- getResp.as[String]
      postData <- postResp.as[String]

      _ = getResp.status shouldBe Status.Ok
      _ = postResp.status shouldBe Status.Ok

      _ = getData shouldBe "getData"
      _ = postData shouldBe "postData"
      } yield ()).unsafeRunSync()
    }
  }
}
