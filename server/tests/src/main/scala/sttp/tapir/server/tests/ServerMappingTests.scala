package sttp.tapir.server.tests

import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.client4._
import sttp.monad.MonadError
import sttp.tapir.tests.Mapping._
import sttp.tapir.tests._
import sttp.tapir.tests.data._

class ServerMappingTests[F[_], OPTIONS, ROUTE](createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE])(implicit m: MonadError[F]) {
  import createServerTest._

  def tests(): List[Test] = List(
    testServer(in_mapped_query_out_string)((fruit: List[Char]) => pureResult(s"fruit length: ${fruit.length}".asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri?fruit=orange").send(backend).map(_.body shouldBe Right("fruit length: 6"))
    },
    testServer(in_mapped_path_out_string)((fruit: Fruit) => pureResult(s"$fruit".asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/fruit/kiwi").send(backend).map(_.body shouldBe Right("Fruit(kiwi)"))
    },
    testServer(in_mapped_path_path_out_string)((p1: FruitAmount) => pureResult(s"FA: $p1".asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/fruit/orange/amount/10").send(backend).map(_.body shouldBe Right("FA: FruitAmount(orange,10)"))
    },
    testServer(in_query_mapped_path_path_out_string) { case (fa: FruitAmount, color: String) =>
      pureResult(s"FA: $fa color: $color".asRight[Unit])
    } { (backend, baseUri) =>
      basicRequest
        .get(uri"$baseUri/fruit/orange/amount/10?color=yellow")
        .send(backend)
        .map(_.body shouldBe Right("FA: FruitAmount(orange,10) color: yellow"))
    },
    testServer(in_query_out_mapped_string)((p1: String) => pureResult(p1.toList.asRight[Unit])) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri?fruit=orange").send(backend).map(_.body shouldBe Right("orange"))
    },
    testServer(in_query_out_mapped_string_header)((p1: String) => pureResult(FruitAmount(p1, p1.length).asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri?fruit=orange").send(backend).map { r =>
          r.body shouldBe Right("orange")
          r.header("X-Role") shouldBe Some("6")
        }
    },
    testServer(in_header_out_header_unit_extended)(in => pureResult(in.asRight[Unit])) { (backend, baseUri) =>
      basicRequest
        .get(uri"$baseUri")
        .header("A", "1")
        .header("X", "3")
        .send(backend)
        .map(_.headers.map(h => h.name.toLowerCase -> h.value).toSet should contain allOf ("y" -> "3", "b" -> "2"))
    },
    testServer(in_4query_out_4header_extended)(in => pureResult(in.asRight[Unit])) { (backend, baseUri) =>
      basicRequest
        .get(uri"$baseUri?a=1&b=2&x=3&y=4")
        .send(backend)
        .map(_.headers.map(h => h.name.toLowerCase -> h.value).toSet should contain allOf ("a" -> "1", "b" -> "2", "x" -> "3", "y" -> "4"))
    },
    testServer(in_3query_out_3header_mapped_to_tuple)(in => pureResult(in.asRight[Unit])) { (backend, baseUri) =>
      basicRequest
        .get(uri"$baseUri?p1=1&p2=2&p3=3")
        .send(backend)
        .map(_.headers.map(h => h.name.toLowerCase -> h.value).toSet should contain allOf ("p1" -> "1", "p2" -> "2", "p3" -> "3"))
    },
    testServer(in_2query_out_2query_mapped_to_unit)(in => pureResult(in.asRight[Unit])) { (backend, baseUri) =>
      basicRequest
        .get(uri"$baseUri?p1=1&p2=2")
        .send(backend)
        .map(_.headers.map(h => h.name.toLowerCase -> h.value).toSet should contain allOf ("p1" -> "DEFAULT_HEADER", "p2" -> "2"))
    }
  )
}
