package tapir.client.tests

import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeBuilder
import tapir._
import tapir.tests._
import tapir.typelevel.ParamsAsArgs

import scala.util.Random

trait ClientTests extends FunSuite with Matchers with BeforeAndAfterAll {

  testClient(endpoint, (), Right(()))
  testClient(in_query_out_text, "apple", Right("fruit: apple"))
  // TODO: in_query_query_out_text
  // TODO: in_header_out_text
  testClient(in_path_path_out_text, ("apple", 10), Right("apple 10 None"))
  testClient(in_text_out_text, "delicious", Right("delicious"))
  testClient(in_mapped_query_out_text, "apple".toList, Right("fruit: apple"))
  testClient(in_mapped_path_path_out_text, FruitAmount("apple", 10), Right("apple 10 None"))
  testClient(in_query_mapped_path_path_out_text, (FruitAmount("apple", 10), "red"), Right("apple 10 Some(red)"))
  testClient(in_query_out_mapped_text, "apple", Right("fruit: apple".toList))
  testClient(in_query_out_mapped_text_header, "apple", Right(FruitAmount("fruit: apple", 5)))

  //

  private object fruitParam extends QueryParamDecoderMatcher[String]("fruit")
  private object colorOptParam extends OptionalQueryParamDecoderMatcher[String]("color")

  private val service = HttpService[IO] {
    case GET -> Root :? fruitParam(v)                                    => Ok(s"fruit: $v", Header("X-Role", v.length.toString))
    case GET -> Root / "fruit" / v1 / "amount" / v2 :? colorOptParam(p1) => Ok(s"$v1 $v2 $p1")
    case r @ POST -> Root / "fruit" / "info"                             => r.as[String].flatMap(Ok(_))
    case GET -> Root                                                     => Ok()
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
