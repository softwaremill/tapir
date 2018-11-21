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

  testServer(endpoint.in(query[String]("param1")).out(textBody[String]), (p1: String) => pureResult(s"param1: $p1".asRight[Unit])) {
    baseUri =>
      sttp.get(uri"$baseUri?param1=value1").send().map(_.body shouldBe Right("param1: value1"))
  }

  testServer(
    endpoint.in(query[String]("param1")).in(query[Option[Int]]("param2")).out(textBody[String]),
    (p1: String, p2: Option[Int]) => pureResult(s"$p1 $p2".asRight[Unit])
  ) { baseUri =>
    sttp.get(uri"$baseUri?param1=value1").send().map(_.body shouldBe Right("value1 None")) *>
      sttp.get(uri"$baseUri?param1=value1&param2=10").send().map(_.body shouldBe Right("value1 Some(10)"))
  }

  testServer(endpoint.in(header[String]("test-header")).out(textBody[String]), (p1: String) => pureResult(s"$p1".asRight[Unit])) {
    baseUri =>
      sttp.get(uri"$baseUri").header("test-header", "test-value").send().map(_.body shouldBe Right("test-value"))
  }

  testServer(endpoint.in("api" / path[String] / "user" / path[Int]).out(textBody[String]),
             (p1: String, p2: Int) => pureResult(s"$p1 $p2".asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri/api/p1/user/20").send().map(_.body shouldBe Right("p1 20"))
  }

  //

  implicit val backend: SttpBackend[IO, Nothing] = AsyncHttpClientCatsBackend[IO]()

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }

  //

  type Port = Int

  def pureResult[T](t: T): R[T]

  def server[I, E, O, FN[_]](e: Endpoint[I, E, O], port: Port, fn: FN[R[Either[E, O]]])(
      implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Resource[IO, Unit]

  def testServer[I, E, O, FN[_]](e: Endpoint[I, E, O], fn: FN[R[Either[E, O]]])(runTest: Uri => IO[Unit])(
      implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Unit = {
    val resources = for {
      port <- Resource.liftF(IO(randomPort()))
      _ <- server(e, port, fn)
    } yield uri"http://localhost:$port"

    test(e.show)(resources.use(runTest).unsafeRunSync())
  }

  //

  private val random = new Random()
  private def randomPort(): Port = random.nextInt(29232) + 32768
}
