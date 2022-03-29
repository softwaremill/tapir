package sttp.tapir.server.interpreter

import sttp.tapir._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future
import scala.collection.immutable.Seq

class FilterServerEndpointsTest extends AnyFlatSpec with Matchers {
  it should "filter endpoints with a single fixed path component" in {
    val e1 = endpoint.in("x").noLogic
    val e2 = endpoint.in("y").noLogic

    val filter = FilterServerEndpoints(List(e1, e2))

    filter(requestWithPath("x")) shouldBe List(e1)
    filter(requestWithPath("y")) shouldBe List(e2)
    filter(requestWithPath("z")) shouldBe Nil
    filter(requestWithPath("x/1")) shouldBe Nil
  }

  it should "filter endpoints with an empty fixed path component" in {
    val e1 = endpoint.in("").noLogic

    val filter = FilterServerEndpoints(List(e1))

    filter(requestWithPath("")) shouldBe List(e1)
    filter(requestWithPath("x")) shouldBe Nil
  }

  it should "not filter endpoints without any path component" in {
    val e1 = endpoint.noLogic

    val filter = FilterServerEndpoints(List(e1))

    filter(requestWithPath("")) shouldBe List(e1)
    filter(requestWithPath("x")) shouldBe List(e1)
    filter(requestWithPath("x/y")) shouldBe List(e1)
  }

  it should "filter endpoints with multiple fixed path components" in {
    val e1 = endpoint.in("x" / "a").noLogic
    val e2 = endpoint.in("x" / "b").noLogic
    val e3 = endpoint.in("y").noLogic

    val filter = FilterServerEndpoints(List(e1, e2, e3))

    filter(requestWithPath("x/a")) shouldBe List(e1)
    filter(requestWithPath("x/b")) shouldBe List(e2)
    filter(requestWithPath("y")) shouldBe List(e3)

    filter(requestWithPath("x")) shouldBe Nil
    filter(requestWithPath("x/c")) shouldBe Nil
    filter(requestWithPath("z")) shouldBe Nil
  }

  it should "filter endpoints with a single fixed and a single path capture component" in {
    val e1 = endpoint.in("x").noLogic
    val e2 = endpoint.in(path[String]("py")).noLogic

    val filter = FilterServerEndpoints(List(e1, e2))

    filter(requestWithPath("x")) shouldBe List(e1, e2)
    filter(requestWithPath("y")) shouldBe List(e2)

    filter(requestWithPath("x/1")) shouldBe Nil
    filter(requestWithPath("y/1")) shouldBe Nil
  }

  it should "filter endpoints with multiple fixed and single capture components" in {
    val e1 = endpoint.in("x" / "a").noLogic
    val e2 = endpoint.in("x" / path[String]("pb")).noLogic
    val e3 = endpoint.in("y" / path[String]("pa") / "i").noLogic
    val e4 = endpoint.in("y" / path[String]("pa") / "j").noLogic

    val filter = FilterServerEndpoints(List(e1, e2, e3, e4))

    filter(requestWithPath("x/a")) shouldBe List(e1, e2)
    filter(requestWithPath("x/b")) shouldBe List(e2)
    filter(requestWithPath("y/a/i")) shouldBe List(e3)
    filter(requestWithPath("y/a/j")) shouldBe List(e4)

    filter(requestWithPath("x")) shouldBe Nil
    filter(requestWithPath("x/b/i")) shouldBe Nil
    filter(requestWithPath("y/a")) shouldBe Nil
    filter(requestWithPath("y/a/k")) shouldBe Nil
  }

  it should "filter endpoints with multiple fixed and capture components" in {
    val e1 = endpoint.in("x" / path[String]("pa") / path[String]("pi")).noLogic
    val e2 = endpoint.in("y" / path[String]("pa") / path[String]("pi") / "z").noLogic

    val filter = FilterServerEndpoints(List(e1, e2))

    filter(requestWithPath("x/a/i")) shouldBe List(e1)
    filter(requestWithPath("y/a/i/z")) shouldBe List(e2)

    filter(requestWithPath("x/a/i/z")) shouldBe Nil
    filter(requestWithPath("y/a/i/z/o")) shouldBe Nil
  }

  it should "filter endpoints with fixed and paths components" in {
    val e1 = endpoint.in("x" / "a").noLogic
    val e2 = endpoint.in("y" / "b" / "i").noLogic
    val e3 = endpoint.in("y" / "b").noLogic
    val e4 = endpoint.in("y" / paths).noLogic

    val filter = FilterServerEndpoints(List(e1, e2, e3, e4))

    filter(requestWithPath("x/a")) shouldBe List(e1)
    filter(requestWithPath("y/b/i")) shouldBe List(e2, e4)
    filter(requestWithPath("y/b")) shouldBe List(e3, e4)
    filter(requestWithPath("y/c")) shouldBe List(e4)
    filter(requestWithPath("y")) shouldBe List(e4)

    filter(requestWithPath("x/b")) shouldBe Nil
  }

  it should "filter endpoints with fixed, capture and paths components" in {
    val e1 = endpoint.in("x" / "a").noLogic
    val e2 = endpoint.in("x" / path[String]("pa")).noLogic
    val e3 = endpoint.in("x" / paths).noLogic

    val filter = FilterServerEndpoints(List(e1, e2, e3))

    filter(requestWithPath("x/a")) shouldBe List(e1, e2, e3)
    filter(requestWithPath("x/b")) shouldBe List(e2, e3)

    filter(requestWithPath("x")) shouldBe List(e3)
    filter(requestWithPath("x/c/d")) shouldBe List(e3)

    filter(requestWithPath("y")) shouldBe Nil
  }

  implicit class NoLogic[I, E](e: PublicEndpoint[I, E, Unit, Any]) {
    def noLogic: ServerEndpoint[Any, Future] = e.serverLogicPure[Future](_ => Right(()))
  }

  def requestWithPath(path: String): ServerRequest = new ServerRequest {
    override def pathSegments: List[String] = if (path == "") Nil else path.split("/").toList

    override def protocol: String = ???
    override def connectionInfo: ConnectionInfo = ???
    override def underlying: Any = ???
    override def queryParameters: QueryParams = ???
    override def withUnderlying(underlying: Any): ServerRequest = ???
    override def method: Method = ???
    override def uri: Uri = ???
    override def attribute[T](k: AttributeKey[T]): Option[T] = ???
    override def attribute[T](k: AttributeKey[T], v: T): ServerRequest = ???
    override def headers: Seq[Header] = ???
  }
}
