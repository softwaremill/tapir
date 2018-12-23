package tapir.client.tests

import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.util.CaseInsensitiveString
import tapir._
import tapir.tests._
import tapir.typelevel.ParamsAsArgs

import scala.util.Random

trait ClientTests extends FunSuite with Matchers with BeforeAndAfterAll {

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
  testClient(in_json_out_string, FruitAmount("orange", 11), Right("""{"fruit":"orange","amount":11}"""))
  testClient(in_string_out_json, """{"fruit":"banana","amount":12}""", Right(FruitAmount("banana", 12)))
  testClient(in_byte_array_out_int, "banana kiwi".getBytes(), Right(11))
  testClient(in_string_out_byte_list, "mango", Right("mango".getBytes.toList))

  //

  private object fruitParam extends QueryParamDecoderMatcher[String]("fruit")
  private object amountOptParam extends OptionalQueryParamDecoderMatcher[String]("amount")
  private object colorOptParam extends OptionalQueryParamDecoderMatcher[String]("color")

  private val service = HttpService[IO] {
    case GET -> Root :? fruitParam(f) +& amountOptParam(a) =>
      Ok(s"fruit: $f${a.map(" " + _).getOrElse("")}", Header("X-Role", f.length.toString))
    case GET -> Root / "fruit" / f                                    => Ok(s"$f")
    case GET -> Root / "fruit" / f / "amount" / a :? colorOptParam(c) => Ok(s"$f $a $c")
    case r @ POST -> Root / "api" / "echo"                            => r.as[String].flatMap(Ok(_))
    case r @ POST -> Root / "api" / "length"                          => r.as[String].flatMap(s => Ok(s.length.toString))
    case r @ GET -> Root =>
      r.headers.get(CaseInsensitiveString("X-Role")) match {
        case None    => Ok()
        case Some(h) => Ok("Role: " + h.value)
      }
  }

  //

  type Port = Int

  def send[I, E, O, FN[_]](e: Endpoint[I, E, O], port: Port, args: I)(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): IO[Either[E, O]]

  def testClient[I, E, O, FN[_]](e: Endpoint[I, E, O], args: I, expectedResult: Either[E, O])(
      implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Unit = {

    test(e.show)(send(e, port, args).unsafeRunSync() shouldBe expectedResult)
  }

  private var port: Port = _
  private var server: Server[IO] = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    port = randomPort()
    server = BlazeBuilder[IO]
      .bindHttp(port)
      .mountService(service)
      .start
      .unsafeRunSync()
  }

  override protected def afterAll(): Unit = {
    server.shutdownNow()
    super.afterAll()
  }

  //

  private val random = new Random()
  private def randomPort(): Port = random.nextInt(29232) + 32768
}
