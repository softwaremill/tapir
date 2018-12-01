package tapir.server.tests

import cats.implicits._
import cats.effect.{IO, Resource}
import tapir._
import tapir.tests._
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import com.softwaremill.sttp._
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import tapir.typelevel.ParamsAsArgs

import scala.util.Random

trait ServerTests[R[_]] extends FunSuite with Matchers with BeforeAndAfterAll {

  testServer(endpoint, () => pureResult(().asRight[Unit])) { baseUri =>
    sttp.get(baseUri).send().map(_.body shouldBe Right(""))
  }

  testServer(in_query_out_text, (fruit: String) => pureResult(s"fruit: $fruit".asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri?fruit=orange").send().map(_.body shouldBe Right("fruit: orange"))
  }

  testServer(in_query_query_out_text, (fruit: String, amount: Option[Int]) => pureResult(s"$fruit $amount".asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri?fruit=orange").send().map(_.body shouldBe Right("orange None")) *>
      sttp.get(uri"$baseUri?fruit=orange&amount=10").send().map(_.body shouldBe Right("orange Some(10)"))
  }

  testServer(in_header_out_text, (p1: String) => pureResult(s"$p1".asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri").header("X-Role", "Admin").send().map(_.body shouldBe Right("Admin"))
  }

  testServer(in_path_path_out_text, (fruit: String, amount: Int) => pureResult(s"$fruit $amount".asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri/fruit/orange/amount/20").send().map(_.body shouldBe Right("orange 20"))
  }

  testServer(in_text_out_text, (b: String) => pureResult(b.asRight[Unit])) { baseUri =>
    sttp.post(uri"$baseUri/fruit/info").body("Sweet").send().map(_.body shouldBe Right("Sweet"))
  }

  testServer(in_mapped_query_out_text, (fruit: List[Char]) => pureResult(s"fruit length: ${fruit.length}".asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri?fruit=orange").send().map(_.body shouldBe Right("fruit length: 6"))
  }

  testServer(in_mapped_path_path_out_text, (p1: FruitAmount) => pureResult(s"FA: $p1".asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri/fruit/orange/amount/10").send().map(_.body shouldBe Right("FA: FruitAmount(orange,10)"))
  }

  testServer(in_query_mapped_path_path_out_text, (fa: FruitAmount, color: String) => pureResult(s"FA: $fa color: $color".asRight[Unit])) {
    baseUri =>
      sttp
        .get(uri"$baseUri/fruit/orange/amount/10?color=yellow")
        .send()
        .map(_.body shouldBe Right("FA: FruitAmount(orange,10) color: yellow"))
  }

  testServer(in_query_out_mapped_text, (p1: String) => pureResult(p1.toList.asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri?fruit=orange").send().map(_.body shouldBe Right("orange"))
  }

  testServer(in_query_out_mapped_text_header, (p1: String) => pureResult(FruitAmount(p1, p1.length).asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri?fruit=orange").send().map { r =>
      r.body shouldBe Right("orange")
      r.header("X-Role") shouldBe Some("6")
    }
  }

  testServer(in_json_out_text, (fa: FruitAmount) => pureResult(s"${fa.fruit} ${fa.amount}".asRight[Unit])) { baseUri =>
    sttp.post(uri"$baseUri/fruit/info").body("""{"fruit":"apple","amount":10}""").send().map(_.body shouldBe Right("apple 10"))
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
