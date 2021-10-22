package sttp.tapir.server.tests

import cats.implicits._
import org.scalatest.matchers.should.Matchers._
import sttp.client3._
import sttp.model._
import sttp.monad.MonadError
import sttp.tapir.tests.OneOf.{
  in_int_out_value_form_exact_match,
  in_string_out_error_detail_nested,
  in_string_out_status_from_string,
  in_string_out_status_from_string_one_empty,
  in_string_out_status_from_type_erasure_using_partial_matcher,
  out_json_or_default_json
}
import sttp.tapir.tests._
import sttp.tapir.tests.data._

class ServerOneOfTests[F[_], ROUTE](
    createServerTest: CreateServerTest[F, Any, ROUTE]
)(implicit
    m: MonadError[F]
) {
  import createServerTest._

  def tests(): List[Test] = List(
    testServer(in_string_out_status_from_string)((v: String) => pureResult((if (v == "apple") Right("x") else Left(10)).asRight[Unit])) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri?fruit=apple").send(backend).map(_.code shouldBe StatusCode.Ok) >>
          basicRequest.get(uri"$baseUri?fruit=orange").send(backend).map(_.code shouldBe StatusCode.Accepted)
    },
    testServer(in_int_out_value_form_exact_match)((num: Int) => pureResult(if (num % 2 == 0) Right("A") else Right("B"))) {
      (backend, baseUri) =>
        basicRequest.get(uri"$baseUri/mapping?num=1").send(backend).map(_.code shouldBe StatusCode.Ok) >>
          basicRequest.get(uri"$baseUri/mapping?num=2").send(backend).map(_.code shouldBe StatusCode.Accepted)
    },
    testServer(in_string_out_status_from_string_one_empty)((v: String) =>
      pureResult((if (v == "apple") Right("x") else Left(())).asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri?fruit=apple").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri?fruit=orange").send(backend).map(_.code shouldBe StatusCode.Accepted)
    },
    testServer(out_json_or_default_json)(entityType =>
      pureResult((if (entityType == "person") Person("mary", 20) else Organization("work")).asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri/entity/person").send(backend).map { r =>
        r.code shouldBe StatusCode.Created
        r.body.right.get should include("mary")
      } >>
        basicRequest.get(uri"$baseUri/entity/org").send(backend).map { r =>
          r.code shouldBe StatusCode.Ok
          r.body.right.get should include("work")
        }
    },
    testServer(in_string_out_status_from_type_erasure_using_partial_matcher)((v: String) =>
      pureResult((if (v == "right") Some(Right("right")) else if (v == "left") Some(Left(42)) else None).asRight[Unit])
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri?fruit=nothing").send(backend).map(_.code shouldBe StatusCode.NoContent) >>
        basicRequest.get(uri"$baseUri?fruit=right").send(backend).map(_.code shouldBe StatusCode.Ok) >>
        basicRequest.get(uri"$baseUri?fruit=left").send(backend).map(_.code shouldBe StatusCode.Accepted)
    },
    testServer(in_string_out_error_detail_nested)((v: String) =>
      pureResult {
        v match {
          case "apple"           => Left(FruitErrorDetail.NotYetGrown(10))
          case "orange"          => Left(FruitErrorDetail.AlreadyPicked("orange"))
          case "pear"            => Right(())
          case _ if v.length < 3 => Left(FruitErrorDetail.NameTooShort(v.length))
          case _                 => Left(FruitErrorDetail.Unknown(List("pear")))
        }
      }
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri?fruit=apple").send(backend).map(_.code shouldBe StatusCode.BadRequest) >>
        basicRequest
          .response(asStringAlways)
          .get(uri"$baseUri?fruit=apple")
          .send(backend)
          .map(_.body should include("\"availableInDays\"")) >>
        basicRequest.response(asStringAlways).get(uri"$baseUri?fruit=orange").send(backend).map(_.body should include("\"name\"")) >>
        basicRequest.response(asStringAlways).get(uri"$baseUri?fruit=m").send(backend).map(_.body should include("\"length\"")) >>
        basicRequest
          .response(asStringAlways)
          .get(uri"$baseUri?fruit=tomato")
          .send(backend)
          .map(_.body should include("\"availableFruit\"")) >>
        basicRequest.get(uri"$baseUri?fruit=pear").send(backend).map(_.code shouldBe StatusCode.Ok)
    }
  )
}
