package tapir.server.tests

import cats.implicits._
import cats.effect.{IO, Resource}
import tapir._
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import com.softwaremill.sttp._
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import tapir.typelevel.ParamsAsArgs

import scala.util.Random

trait ServerTests[R[_]] extends FunSuite with Matchers with BeforeAndAfterAll {

  type Port = Int

  implicit val backend: SttpBackend[IO, Nothing] = AsyncHttpClientCatsBackend[IO]()

  def pureResult[T](t: T): R[T]

  def server[I, E, O, FN[_]](e: Endpoint[I, E, O], port: Int, fn: FN[R[Either[E, O]]])(
      implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Resource[IO, Unit]

  def doTest[I, E, O, FN[_]](e: Endpoint[I, E, O], fn: FN[R[Either[E, O]]])(runTest: Uri => IO[Unit])(
      implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Unit = {
    val resources = for {
      port <- Resource.liftF(IO(randomPort()))
      _ <- server(e, port, fn)
    } yield uri"http://localhost:$port"

    test(e.show)(resources.use(runTest).unsafeRunSync())
  }

  doTest(endpoint.in(query[String]("param1")).out(textBody[String]), (p1: String) => pureResult(s"param1: $p1".asRight[NoParams])) {
    baseUri =>
      sttp.get(uri"$baseUri?param1=value1").send().map(_.body shouldBe Right("param1: value1"))
  }

  doTest(
    endpoint.in(query[String]("param1")).in(query[Option[Int]]("param2")).out(textBody[String]),
    (p1: String, p2: Option[Int]) => pureResult(s"$p1 $p2".asRight[NoParams])
  ) { baseUri =>
    sttp.get(uri"$baseUri?param1=value1").send().map(_.body shouldBe Right("value1 None")) *>
      sttp.get(uri"$baseUri?param1=value1&param2=10").send().map(_.body shouldBe Right("value1 Some(10)"))
  }

  doTest(endpoint.in(header[String]("test-header")).out(textBody[String]), (p1: String) => pureResult(s"$p1".asRight[NoParams])) {
    baseUri =>
      sttp.get(uri"$baseUri").header("test-header", "test-value").send().map(_.body shouldBe Right("test-value"))
  }

  doTest(endpoint.in("api" / path[String] / "user" / path[Int]).out(textBody[String]),
         (p1: String, p2: Int) => pureResult(s"$p1 $p2".asRight[NoParams])) { baseUri =>
    sttp.get(uri"$baseUri/api/p1/user/20").send().map(_.body shouldBe Right("p1 20"))
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }

  private val random = new Random()
  private def randomPort(): Port = random.nextInt(29232) + 32768

  def doTest(name: String)(io: IO[Unit]): Unit = {
    test(name)(io.unsafeRunSync())
  }
}
