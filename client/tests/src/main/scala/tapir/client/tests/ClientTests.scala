package tapir.client.tests

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
import tapir._
import tapir.tests._
import tapir.typelevel.ParamsAsArgs
import TestUtil._
import org.http4s.multipart
import tapir.model.{MultiQueryParams, StatusCodes, UsernamePassword}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Random

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
  testClient(in_input_stream_out_input_stream,
             new ByteArrayInputStream("mango".getBytes),
             Right(new ByteArrayInputStream("mango".getBytes)))
  testClient(in_file_out_file, testFile, Right(testFile))
  testClient(in_form_out_form, FruitAmount("plum", 10), Right(FruitAmount("plum", 10)))
  testClient(
    in_query_params_out_string,
    MultiQueryParams.fromMap(Map("name" -> "apple", "weight" -> "42", "kind" -> "very good")),
    Right("kind=very good&name=apple&weight=42")
  )
  testClient(in_paths_out_string, Seq("fruit", "apple", "amount", "50"), Right("apple 50 None"))
  testClient(in_query_list_out_header_list, List("plum", "watermelon", "apple"), Right(List("apple", "watermelon", "plum")))
  testClient(in_simple_multipart_out_string, FruitAmount("melon", 10), Right("melon=10"))
  testClient(in_cookie_cookie_out_header, (23, "pomegranate"), Right(List("etanargemop=2c"))) // for some reason, http4s keeps only the last cookie header
  // TODO: test root path
  testClient(in_auth_apikey_header_out_string, "1234", Right("Authorization=None; X-Api-Key=Some(1234); Query=None"))
  testClient(in_auth_apikey_query_out_string, "1234", Right("Authorization=None; X-Api-Key=None; Query=Some(1234)"))
  testClient(in_auth_basic_out_string,
             UsernamePassword("teddy", Some("bear")),
             Right("Authorization=Some(Basic dGVkZHk6YmVhcg==); X-Api-Key=None; Query=None"))
  testClient(in_auth_bearer_out_string, "1234", Right("Authorization=Some(Bearer 1234); X-Api-Key=None; Query=None"))
  testClient(in_string_out_status_from_string, "apple", Right("fruit: apple")) // status from should be a no-op from the client interpreter's point of view
  testClient(in_string_out_status, "apple", Right(StatusCodes.Ok))

  //

  test(in_headers_out_headers.show) {
    send(in_headers_out_headers, port, List(("X-Fruit", "apple"), ("Y-Fruit", "Orange")))
      .unsafeRunSync()
      .right
      .get should contain allOf (("X-Fruit", "elppa"), ("Y-Fruit", "egnarO"))
  }

  //

  def mkStream(s: String): S
  def rmStream(s: S): String

  test(in_stream_out_stream[S].show) {
    rmStream(
      send(in_stream_out_stream[S], port, mkStream("mango cranberry"))
        .unsafeRunSync()
        .right
        .get) shouldBe "mango cranberry"
  }

  //

  private object fruitParam extends QueryParamDecoderMatcher[String]("fruit")
  private object amountOptParam extends OptionalQueryParamDecoderMatcher[String]("amount")
  private object colorOptParam extends OptionalQueryParamDecoderMatcher[String]("color")
  private object apiKeyOptParam extends OptionalQueryParamDecoderMatcher[String]("api-key")

  private val service = HttpRoutes.of[IO] {
    case GET -> Root :? fruitParam(f) +& amountOptParam(amount) =>
      Ok(s"fruit: $f${amount.map(" " + _).getOrElse("")}", Header("X-Role", f.length.toString))
    case GET -> Root / "fruit" / f                                         => Ok(s"$f")
    case GET -> Root / "fruit" / f / "amount" / amount :? colorOptParam(c) => Ok(s"$f $amount $c")
    case r @ GET -> Root / "api" / "echo" / "params"                       => Ok(r.uri.query.params.toSeq.sortBy(_._1).map(p => s"${p._1}=${p._2}").mkString("&"))
    case r @ GET -> Root / "api" / "echo" / "headers"                      => Ok(headers = r.headers.map(h => Header(h.name.value, h.value.reverse)).toSeq: _*)
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
    case r @ GET -> Root / "auth" :? apiKeyOptParam(ak) =>
      val authHeader = r.headers.get(CaseInsensitiveString("Authorization")).map(_.value)
      val xApiKey = r.headers.get(CaseInsensitiveString("X-Api-Key")).map(_.value)
      Ok(s"Authorization=$authHeader; X-Api-Key=$xApiKey; Query=$ak")
  }

  private val app: HttpApp[IO] = Router("/" -> service).orNotFound

  //

  type Port = Int

  def send[I, E, O, FN[_]](e: Endpoint[I, E, O, S], port: Port, args: I)(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): IO[Either[E, O]]

  def testClient[I, E, O, FN[_]](e: Endpoint[I, E, O, S], args: I, expectedResult: Either[E, O])(
      implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Unit = {

    test(e.show) {
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
    port = randomPort()

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

    logger.debug(s"Server exited with code: ${Await.result(status, 2.seconds)}")
  }

  //

  private val random = new Random()
  private def randomPort(): Port = random.nextInt(29232) + 32768
}
