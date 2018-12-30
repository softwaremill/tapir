package tapir.server.tests

import cats.effect.{IO, Resource}
import cats.implicits._
import com.softwaremill.sttp._
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import org.scalatest.{Assertion, BeforeAndAfterAll, FunSuite, Matchers}
import tapir._
import tapir.tests._
import tapir.typelevel.ParamsAsArgs

import scala.util.Random

trait ServerTests[R[_]] extends FunSuite with Matchers with BeforeAndAfterAll {

  testServer(endpoint, () => pureResult(().asRight[Unit])) { baseUri =>
    sttp.get(baseUri).send().map(_.body shouldBe Right(""))
  }

  testServer(in_query_out_string, (fruit: String) => pureResult(s"fruit: $fruit".asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri?fruit=orange").send().map(_.body shouldBe Right("fruit: orange"))
  }

  testServer(in_query_query_out_string, (fruit: String, amount: Option[Int]) => pureResult(s"$fruit $amount".asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri?fruit=orange").send().map(_.body shouldBe Right("orange None")) *>
      sttp.get(uri"$baseUri?fruit=orange&amount=10").send().map(_.body shouldBe Right("orange Some(10)"))
  }

  testServer(in_header_out_string, (p1: String) => pureResult(s"$p1".asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri").header("X-Role", "Admin").send().map(_.body shouldBe Right("Admin"))
  }

  testServer(in_path_path_out_string, (fruit: String, amount: Int) => pureResult(s"$fruit $amount".asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri/fruit/orange/amount/20").send().map(_.body shouldBe Right("orange 20"))
  }

  testServer(in_string_out_string, (b: String) => pureResult(b.asRight[Unit])) { baseUri =>
    sttp.post(uri"$baseUri/api/echo").body("Sweet").send().map(_.body shouldBe Right("Sweet"))
  }

  testServer(in_mapped_query_out_string, (fruit: List[Char]) => pureResult(s"fruit length: ${fruit.length}".asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri?fruit=orange").send().map(_.body shouldBe Right("fruit length: 6"))
  }

  testServer(in_mapped_path_out_string, (fruit: Fruit) => pureResult(s"$fruit".asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri/fruit/kiwi").send().map(_.body shouldBe Right("Fruit(kiwi)"))
  }

  testServer(in_mapped_path_path_out_string, (p1: FruitAmount) => pureResult(s"FA: $p1".asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri/fruit/orange/amount/10").send().map(_.body shouldBe Right("FA: FruitAmount(orange,10)"))
  }

  testServer(in_query_mapped_path_path_out_string, (fa: FruitAmount, color: String) => pureResult(s"FA: $fa color: $color".asRight[Unit])) {
    baseUri =>
      sttp
        .get(uri"$baseUri/fruit/orange/amount/10?color=yellow")
        .send()
        .map(_.body shouldBe Right("FA: FruitAmount(orange,10) color: yellow"))
  }

  testServer(in_query_out_mapped_string, (p1: String) => pureResult(p1.toList.asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri?fruit=orange").send().map(_.body shouldBe Right("orange"))
  }

  testServer(in_query_out_mapped_string_header, (p1: String) => pureResult(FruitAmount(p1, p1.length).asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri?fruit=orange").send().map { r =>
      r.body shouldBe Right("orange")
      r.header("X-Role") shouldBe Some("6")
    }
  }

  testServer(in_json_out_string, (fa: FruitAmount) => pureResult(s"${fa.fruit} ${fa.amount}".asRight[Unit])) { baseUri =>
    sttp.post(uri"$baseUri/api/echo").body("""{"fruit":"orange","amount":11}""").send().map(_.body shouldBe Right("orange 11"))
  }

  testServer(in_string_out_json, (s: String) => pureResult(FruitAmount(s.split(" ")(0), s.split(" ")(1).toInt).asRight[Unit])) { baseUri =>
    sttp.post(uri"$baseUri/api/echo").body("""banana 12""").send().map(_.body shouldBe Right("""{"fruit":"banana","amount":12}"""))
  }

  testServer(in_string_out_json,
             (s: String) => pureResult(FruitAmount(s.split(" ")(0), s.split(" ")(1).toInt).asRight[Unit]),
             " with accept header") { baseUri =>
    sttp
      .post(uri"$baseUri/api/echo")
      .body("""banana 12""")
      .header(HeaderNames.Accept, MediaTypes.Json)
      .send()
      .map(_.body shouldBe Right("""{"fruit":"banana","amount":12}"""))
  }

  testServer(in_byte_array_out_int, (b: Array[Byte]) => pureResult(b.length.asRight[Unit])) { baseUri =>
    sttp.post(uri"$baseUri/api/length").body("banana kiwi".getBytes).send().map(_.body shouldBe Right("11"))
  }

  testServer(in_string_out_byte_list, (s: String) => pureResult(s.getBytes.toList.asRight[Unit])) { baseUri =>
    sttp.post(uri"$baseUri/api/echo").body("mango").response(asByteArray).send().map(_.body.map(new String(_)) shouldBe Right("mango"))
  }

  testServer(endpoint_with_path, () => pureResult("".asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri/not-existing-path").send().map(_.code shouldBe tapir.StatusCodes.NotFound)
  }

  testServer(endpoint_with_status_mapping_no_body, () => pureResult(().asRight[Unit]), "status mapping") { baseUri =>
    sttp.get(uri"$baseUri/api").send().map(_.code shouldBe tapir.StatusCodes.AlreadyReported)
  }

  testServer(endpoint_with_status_mapping, () => pureResult("".asRight[Unit]), "status mapping") { baseUri =>
    sttp.get(uri"$baseUri/api").send().map(_.code shouldBe tapir.StatusCodes.AlreadyReported)
  }

  testServer(endpoint_with_error_status_mapping, () => pureResult("".asLeft[Unit])) { baseUri =>
    sttp.get(uri"$baseUri/api").send().map(_.code shouldBe tapir.StatusCodes.TooManyRequests)
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

  def testServer[I, E, O, FN[_]](e: Endpoint[I, E, O], fn: FN[R[Either[E, O]]], testNameSuffix: String = "")(runTest: Uri => IO[Assertion])(
      implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Unit = {
    val resources = for {
      port <- Resource.liftF(IO(randomPort()))
      _ <- server(e, port, fn)
    } yield uri"http://localhost:$port"

    test(e.show + testNameSuffix)(resources.use(runTest).unsafeRunSync())
  }

  //

  private val random = new Random()
  private def randomPort(): Port = random.nextInt(29232) + 32768
}
