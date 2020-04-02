package sttp.tapir.client.tests

import java.io.{ByteArrayInputStream, File, InputStream}
import java.nio.ByteBuffer

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.concurrent.SignallingRef
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import org.http4s.util.CaseInsensitiveString
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import sttp.tapir.{DecodeResult, _}
import sttp.tapir.tests._
import TestUtil._
import org.http4s.multipart
import sttp.model.{MultiQueryParams, StatusCode}
import sttp.tapir.model.UsernamePassword

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

trait ClientTests[S] extends FunSuite with Matchers with BeforeAndAfterAll {
  private val logger = org.log4s.getLogger

  implicit private val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit private val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit private val timer: Timer[IO] = IO.timer(ec)

  private val testFile = writeToFile("pen pineapple apple pen")

  testClient(endpoint, (), Right(()))
  testClient(in_query_out_string, "apple", Right("fruit: apple"))
  testClient(in_query_query_out_string, ("apple", Some(10)), Right("fruit: apple 10"))
  testClient(in_header_out_string, "Admin", Right("Role: Admin"))
  testClient(in_path_path_out_string, ("apple", 10), Right("apple 10 None"))
  testClient(in_string_out_string, "delicious", Right("delicious"))
  testClient(in_mapped_query_out_string, "apple".toList, Right("fruit: apple"))
  testClient(in_mapped_path_out_string, Fruit("kiwi"), Right("kiwi"))
  testClient(in_mapped_path_path_out_string, FruitAmount("apple", 10), Right("apple 10 None"))
  testClient(in_query_mapped_path_path_out_string, (FruitAmount("apple", 10), "red"), Right("apple 10 Some(red)"))
  testClient(in_query_out_mapped_string, "apple", Right("fruit: apple".toList))
  testClient(in_query_out_mapped_string_header, "apple", Right(FruitAmount("fruit: apple", 5)))
  testClient(in_json_out_json, FruitAmount("orange", 11), Right(FruitAmount("orange", 11)))
  testClient(in_byte_array_out_byte_array, "banana kiwi".getBytes(), Right("banana kiwi".getBytes()))
  testClient(in_byte_buffer_out_byte_buffer, ByteBuffer.wrap("mango".getBytes), Right(ByteBuffer.wrap("mango".getBytes)))
  testClient(
    in_input_stream_out_input_stream,
    new ByteArrayInputStream("mango".getBytes),
    Right(new ByteArrayInputStream("mango".getBytes))
  )
  testClient(in_file_out_file, testFile, Right(testFile))
  testClient(in_form_out_form, FruitAmount("plum", 10), Right(FruitAmount("plum", 10)))
  testClient(
    in_query_params_out_string,
    MultiQueryParams.fromMap(Map("name" -> "apple", "weight" -> "42", "kind" -> "very good")),
    Right("kind=very good&name=apple&weight=42")
  )
  testClient(in_paths_out_string, List("fruit", "apple", "amount", "50"), Right("apple 50 None"))
  testClient(in_query_list_out_header_list, List("plum", "watermelon", "apple"), Right(List("apple", "watermelon", "plum")))
  testClient(in_simple_multipart_out_string, FruitAmount("melon", 10), Right("melon=10"))
  testClient(in_cookie_cookie_out_header, (23, "pomegranate"), Right(List("etanargemop=2c ;32=1c")))
  // TODO: test root path
  testClient(in_auth_apikey_header_out_string, "1234", Right("Authorization=None; X-Api-Key=Some(1234); Query=None"))
  testClient(in_auth_apikey_query_out_string, "1234", Right("Authorization=None; X-Api-Key=None; Query=Some(1234)"))
  testClient(
    in_auth_basic_out_string,
    UsernamePassword("teddy", Some("bear")),
    Right("Authorization=Some(Basic dGVkZHk6YmVhcg==); X-Api-Key=None; Query=None")
  )
  testClient(in_auth_bearer_out_string, "1234", Right("Authorization=Some(Bearer 1234); X-Api-Key=None; Query=None"))
  testClient(in_string_out_status_from_string.name("status one of 1"), "apple", Right(Right("fruit: apple")))
  testClient(in_string_out_status_from_string.name("status one of 2"), "papaya", Right(Left(29)))
  testClient(in_string_out_status, "apple", Right(StatusCode.Ok))

  testClient(delete_endpoint, (), Right(()))

  testClient(in_optional_json_out_optional_json.name("defined"), Some(FruitAmount("orange", 11)), Right(Some(FruitAmount("orange", 11))))
  testClient(in_optional_json_out_optional_json.name("empty"), None, Right(None))

  //

  test(in_headers_out_headers.showDetail) {
    send(
      in_headers_out_headers,
      port,
      List(sttp.model.Header.notValidated("X-Fruit", "apple"), sttp.model.Header.notValidated("Y-Fruit", "Orange"))
    ).unsafeRunSync().right.get should contain allOf (sttp.model.Header.notValidated("X-Fruit", "elppa"), sttp.model.Header
      .notValidated("Y-Fruit", "egnarO"))
  }

  test(in_json_out_headers.showDetail) {
    send(in_json_out_headers, port, FruitAmount("apple", 10))
      .unsafeRunSync()
      .right
      .get should contain(sttp.model.Header.notValidated("Content-Type", "application/json".reverse))
  }

  testClient[Unit, Unit, Unit, Nothing](in_unit_out_json_unit, (), Right(()))

  test(in_simple_multipart_out_raw_string.showDetail) {
    val result = send(in_simple_multipart_out_raw_string, port, FruitAmountWrapper(FruitAmount("apple", 10), "Now!"))
      .unsafeRunSync()
      .right
      .get

    val indexOfJson = result.indexOf("{\"fruit")
    val beforeJson = result.substring(0, indexOfJson)
    val afterJson = result.substring(indexOfJson)

    beforeJson should include("""Content-Disposition: form-data; name="fruitAmount"""")
    beforeJson should include("Content-Type: application/json")
    beforeJson should not include ("Content-Type: text/plain")

    afterJson should include("""Content-Disposition: form-data; name="notes"""")
    afterJson should include("Content-Type: text/plain; charset=UTF-8")
    afterJson should not include ("Content-Type: application/json")
  }

  test(in_fixed_header_out_string.showDetail) {
    send(in_fixed_header_out_string, port, ())
      .unsafeRunSync() shouldBe Right("Location: secret")
  }

  //

  def mkStream(s: String): S
  def rmStream(s: S): String

  test(in_stream_out_stream[S].showDetail) {
    rmStream(
      send(in_stream_out_stream[S], port, mkStream("mango cranberry"))
        .unsafeRunSync()
        .right
        .get
    ) shouldBe "mango cranberry"
  }

  test("not existing endpoint, with error output not matching 404") {
    safeSend(not_existing_endpoint, port, ()).unsafeRunSync() should matchPattern {
      case DecodeResult.Error(_, _: IllegalArgumentException) =>
    }
  }

  //

  private object fruitParam extends QueryParamDecoderMatcher[String]("fruit")
  private object amountOptParam extends OptionalQueryParamDecoderMatcher[String]("amount")
  private object colorOptParam extends OptionalQueryParamDecoderMatcher[String]("color")
  private object apiKeyOptParam extends OptionalQueryParamDecoderMatcher[String]("api-key")

  private val service = HttpRoutes.of[IO] {
    case GET -> Root :? fruitParam(f) +& amountOptParam(amount) =>
      if (f == "papaya") {
        Accepted("29")
      } else {
        Ok(s"fruit: $f${amount.map(" " + _).getOrElse("")}", Header("X-Role", f.length.toString))
      }
    case GET -> Root / "fruit" / f                                         => Ok(s"$f")
    case GET -> Root / "fruit" / f / "amount" / amount :? colorOptParam(c) => Ok(s"$f $amount $c")
    case r @ GET -> Root / "api" / "unit"                                  => Ok("{}")
    case r @ GET -> Root / "api" / "echo" / "params"                       => Ok(r.uri.query.params.toSeq.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString("&"))
    case r @ GET -> Root / "api" / "echo" / "headers" =>
      val headers = r.headers.toList.map(h => Header(h.name.value, h.value.reverse))
      val filteredHeaders = r.headers.find(_.name.value == "Cookie") match {
        case Some(c) => headers.filter(_.name.value == "Cookie") :+ Header("Set-Cookie", c.value.reverse)
        case None    => headers
      }
      Ok(headers = filteredHeaders: _*)
    case r @ GET -> Root / "api" / "echo" / "param-to-header" =>
      Ok(headers = r.uri.multiParams.getOrElse("qq", Nil).reverse.map(v => Header("hh", v)): _*)
    case r @ POST -> Root / "api" / "echo" / "multipart" =>
      r.decode[multipart.Multipart[IO]] { mp =>
        val parts: Vector[multipart.Part[IO]] = mp.parts
        def toString(s: fs2.Stream[IO, Byte]): IO[String] = s.through(fs2.text.utf8Decode).compile.foldMonoid
        def partToString(name: String): IO[String] = parts.find(_.name.contains(name)).map(p => toString(p.body)).getOrElse(IO.pure(""))
        partToString("fruit").product(partToString("amount")).flatMap {
          case (fruit, amount) =>
            Ok(s"$fruit=$amount")
        }
      }
    case r @ POST -> Root / "api" / "echo" => r.as[String].flatMap(Ok(_))
    case r @ GET -> Root =>
      r.headers.get(CaseInsensitiveString("X-Role")) match {
        case None    => Ok()
        case Some(h) => Ok("Role: " + h.value)
      }

    case r @ GET -> Root / "secret" =>
      r.headers.get(CaseInsensitiveString("Location")) match {
        case None    => BadRequest()
        case Some(h) => Ok("Location: " + h.value)
      }

    case DELETE -> Root / "api" / "delete" => Ok()

    case r @ GET -> Root / "auth" :? apiKeyOptParam(ak) =>
      val authHeader = r.headers.get(CaseInsensitiveString("Authorization")).map(_.value)
      val xApiKey = r.headers.get(CaseInsensitiveString("X-Api-Key")).map(_.value)
      Ok(s"Authorization=$authHeader; X-Api-Key=$xApiKey; Query=$ak")
  }

  private val app: HttpApp[IO] = Router("/" -> service).orNotFound

  //

  type Port = Int

  def send[I, E, O, FN[_]](e: Endpoint[I, E, O, S], port: Port, args: I): IO[Either[E, O]]
  def safeSend[I, E, O, FN[_]](e: Endpoint[I, E, O, S], port: Port, args: I): IO[DecodeResult[Either[E, O]]]

  def testClient[I, E, O, FN[_]](e: Endpoint[I, E, O, S], args: I, expectedResult: Either[E, O]): Unit = {
    test(e.showDetail) {
      // adjust test result values to a form that is comparable by scalatest
      def adjust(r: Either[Any, Any]): Either[Any, Any] = {
        def doAdjust(v: Any) = v match {
          case is: InputStream => inputStreamToByteArray(is).toList
          case a: Array[Byte]  => a.toList
          case f: File         => readFromFile(f)
          case _               => v
        }

        r.map(doAdjust).left.map(doAdjust)
      }

      adjust(send(e, port, args).unsafeRunSync()) shouldBe adjust(expectedResult)
    }
  }

  private var port: Port = _
  private var exitSignal: SignallingRef[IO, Boolean] = _
  private var serverExitCode: Future[Option[ExitCode]] = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    port = portCounter.next()

    exitSignal = SignallingRef.apply[IO, Boolean](false).unsafeRunSync()

    serverExitCode = BlazeServerBuilder[IO]
      .bindHttp(port)
      .withHttpApp(app)
      .serveWhile(exitSignal, Ref.unsafe(ExitCode.Success))
      .compile
      .last
      .unsafeToFuture()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()

    val status = for {
      _ <- exitSignal.set(true).unsafeToFuture()
      exitCode <- serverExitCode
    } yield exitCode

    logger.debug(s"Server exited with code: ${Await.result(status, 5.seconds)}")
  }

  //

  lazy val portCounter: PortCounter = new PortCounter(41000)
}
