package tapir.client.tests

import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeBuilder
import tapir._
import tapir.typelevel.ParamsAsArgs

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

trait ClientTests extends FunSuite with Matchers with BeforeAndAfterAll {

  type Port = Int

  private object param1 extends QueryParamDecoderMatcher[String]("param1")

  private val service = HttpService[IO] {
    case GET -> Root :? param1(v) => Ok(s"param1: $v")
  }

  def send[I, E, O, FN[_]](e: Endpoint[I, E, O], port: Port, args: I)(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): IO[Either[E, O]]

  def doTest[I, E, O, FN[_]](e: Endpoint[I, E, O], args: I, expectedResult: Either[E, O])(
      implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): Unit = {

    test(e.show)(send(e, port, args).unsafeRunSync() shouldBe expectedResult)
  }

  doTest(endpoint.in(query[String]("param1")).out(textBody[String]), "value1", Right("param1: value1"))

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

  private val random = new Random()
  private def randomPort(): Port = random.nextInt(29232) + 32768
}
