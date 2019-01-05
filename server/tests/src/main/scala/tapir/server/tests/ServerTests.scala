package tapir.server.tests

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.ByteBuffer

import cats.effect.{IO, Resource}
import cats.implicits._
import com.softwaremill.sttp._
import com.softwaremill.sttp.asynchttpclient.cats.AsyncHttpClientCatsBackend
import org.scalatest.{Assertion, BeforeAndAfterAll, FunSuite, Matchers}
import tapir.tests.TestUtil._
import tapir.tests._
import tapir.typelevel.ParamsAsArgs
import tapir.{DefaultStatusMappers, StatusCodes, _}

import scala.util.Random

trait ServerTests[R[_]] extends FunSuite with Matchers with BeforeAndAfterAll {

  testServer(endpoint, () => pureResult(().asRight[Unit])) { baseUri =>
    sttp.get(baseUri).send().map(_.body shouldBe Right(""))
  }

  testServer(endpoint, () => pureResult(().asRight[Unit]), "with post method") { baseUri =>
    sttp.post(baseUri).send().map(_.body shouldBe 'left)
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

  testServer(in_string_out_string, (b: String) => pureResult(b.asRight[Unit]), "with get method") { baseUri =>
    sttp.get(uri"$baseUri/api/echo").body("Sweet").send().map(_.body shouldBe 'left)
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

  testServer(in_json_out_json, (fa: FruitAmount) => pureResult(FruitAmount(fa.fruit + " banana", fa.amount * 2).asRight[Unit])) { baseUri =>
    sttp
      .post(uri"$baseUri/api/echo")
      .body("""{"fruit":"orange","amount":11}""")
      .send()
      .map(_.body shouldBe Right("""{"fruit":"orange banana","amount":22}"""))
  }

  testServer(in_json_out_json, (fa: FruitAmount) => pureResult(fa.asRight[Unit]), "with accept header") { baseUri =>
    sttp
      .post(uri"$baseUri/api/echo")
      .body("""{"fruit":"banana","amount":12}""")
      .header(HeaderNames.Accept, MediaTypes.Json)
      .send()
      .map(_.body shouldBe Right("""{"fruit":"banana","amount":12}"""))
  }

  testServer(in_byte_array_out_byte_array, (b: Array[Byte]) => pureResult(b.asRight[Unit])) { baseUri =>
    sttp.post(uri"$baseUri/api/echo").body("banana kiwi".getBytes).send().map(_.body shouldBe Right("banana kiwi"))
  }

  testServer(in_byte_buffer_out_byte_buffer, (b: ByteBuffer) => pureResult(b.asRight[Unit])) { baseUri =>
    sttp.post(uri"$baseUri/api/echo").body("mango").send().map(_.body shouldBe Right("mango"))
  }

  testServer(in_input_stream_out_input_stream,
             (is: InputStream) => pureResult((new ByteArrayInputStream(inputStreamToByteArray(is)): InputStream).asRight[Unit])) {
    baseUri =>
      sttp.post(uri"$baseUri/api/echo").body("mango").send().map(_.body shouldBe Right("mango"))
  }

  testServer(endpoint_with_path, () => pureResult("".asRight[Unit])) { baseUri =>
    sttp.get(uri"$baseUri/not-existing-path").send().map(_.code shouldBe StatusCodes.NotFound)
  }

  testServer(endpoint_with_status_mapping_no_body, () => pureResult(().asRight[Unit]), "status mapping no body", { _: Unit =>
    StatusCodes.AlreadyReported
  }) { baseUri =>
    sttp.get(uri"$baseUri/api").send().map(_.code shouldBe StatusCodes.AlreadyReported)
  }

  testServer(endpoint_with_status_mapping, () => pureResult("".asRight[Unit]), "status mapping", { _: String =>
    StatusCodes.AlreadyReported
  }) { baseUri =>
    sttp.get(uri"$baseUri/api").send().map(_.code shouldBe StatusCodes.AlreadyReported)
  }

  testServer(endpoint_with_error_status_mapping, () => pureResult("".asLeft[Unit]), errorStatusMapper = { _: String =>
    StatusCodes.TooManyRequests
  }) { baseUri =>
    sttp.get(uri"$baseUri/api").send().map(_.code shouldBe StatusCodes.TooManyRequests)
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

  def server[I, E, O, FN[_]](e: Endpoint[I, E, O],
                             port: Port,
                             fn: FN[R[Either[E, O]]],
                             statusMapper: O => StatusCode,
                             errorStatusMapper: E => StatusCode)(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Resource[IO, Unit]

  def testServer[I, E, O, FN[_]](e: Endpoint[I, E, O],
                                 fn: FN[R[Either[E, O]]],
                                 testNameSuffix: String = "",
                                 statusMapper: O => StatusCode = DefaultStatusMappers.out,
                                 errorStatusMapper: E => StatusCode = DefaultStatusMappers.error)(runTest: Uri => IO[Assertion])(
      implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Unit = {
    val resources = for {
      port <- Resource.liftF(IO(randomPort()))
      _ <- server(e, port, fn, statusMapper, errorStatusMapper)
    } yield uri"http://localhost:$port"

    test(e.show + (if (testNameSuffix == "") "" else " " + testNameSuffix))(resources.use(runTest).unsafeRunSync())
  }

  //

  private val random = new Random()
  private def randomPort(): Port = random.nextInt(29232) + 32768
}
