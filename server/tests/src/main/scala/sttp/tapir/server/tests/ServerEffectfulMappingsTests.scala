package sttp.tapir.server.tests

import cats.effect.IO
import cats.syntax.all._
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.Effect
import sttp.client3._
import sttp.monad.MonadError
import sttp.tapir.tests.{Fruit, FruitAmount, Test}
import sttp.tapir.tests.EffectfulMappings._

class ServerEffectfulMappingsTests[F[_], S, ROUTE](
    backend: SttpBackend[IO, Any],
    serverTests: ServerTests[F, Effect[F], ROUTE]
)(implicit
    m: MonadError[F]
) {

  private def pureResult[T](t: T): F[T] = m.unit(t)

  def tests(): List[Test] = {
    import serverTests._

    List(
      testServer(in_f_query_out_string(m))((f: Fruit) => pureResult(f.f.asRight[Unit])) { baseUri =>
        basicRequest.post(uri"$baseUri?fruit=apple").send(backend).map(_.body shouldBe Right("apple"))
      },
      testServer(in_f_query_query_out_string(m)) { case (f: Fruit, amount: Int) => pureResult(s"${f.f}-$amount".asRight[Unit]) } {
        baseUri =>
          basicRequest.post(uri"$baseUri?fruit=pineapple&amount=10").send(backend).map(_.body shouldBe Right("pineapple-10"))
      },
      testServer(in_f_mapped_query_query_out_string(m))((fa: FruitAmount) => pureResult(s"${fa.fruit}-${fa.amount}".asRight[Unit])) {
        baseUri =>
          basicRequest.post(uri"$baseUri?fruit=apple&amount=10").send(backend).map(_.body shouldBe Right("apple-10"))
      },
      testServer(in_query_out_f_string(m))((f: String) => pureResult(Fruit(f).asRight[Unit])) { baseUri =>
        basicRequest.post(uri"$baseUri?fruit=apple").send(backend).map(_.body shouldBe Right("apple"))
      },
      testServer(in_query_out_f_string_int(m))((f: String) => pureResult((Fruit(f), 12).asRight[Unit])) { baseUri =>
        basicRequest.post(uri"$baseUri?fruit=pineapple").send(backend).map { r =>
          r.body shouldBe Right("pineapple")
          r.header("X-Role") shouldBe Some("12")
        }
      },
      testServer(in_query_out_f_mapped_string_int(m))((f: String) => pureResult(FruitAmount(f, 21).asRight[Unit])) { baseUri =>
        basicRequest.post(uri"$baseUri?fruit=orange").send(backend).map { r =>
          r.body shouldBe Right("orange")
          r.header("X-Role") shouldBe Some("21")
        }
      }
    )
  }
}
