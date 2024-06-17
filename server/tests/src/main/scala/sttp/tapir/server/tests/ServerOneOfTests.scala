package sttp.tapir.server.tests

import cats.implicits._
import io.circe.generic.auto._
import org.scalatest.EitherValues._
import org.scalatest.matchers.should.Matchers._
import sttp.client3._
import sttp.model._
import sttp.monad.MonadError
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.tests.OneOf._
import sttp.tapir.tests._
import sttp.tapir.tests.data._
import sttp.tapir.{oneOf, _}

class ServerOneOfTests[F[_], OPTIONS, ROUTE](
    createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE]
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
    },
    testServerLogic(
      endpoint
        .securityIn(header[String]("token"))
        .errorOut(jsonBody[FruitErrorDetail.Unknown])
        .serverSecurityLogicSuccess(v => pureResult(v))
        .errorOutVariants[FruitErrorDetail](
          oneOfVariant(jsonBody[FruitErrorDetail.AlreadyPicked]),
          oneOfVariant(jsonBody[FruitErrorDetail.NotYetGrown]),
          oneOfVariant(jsonBody[FruitErrorDetail.NameTooShort])
        )
        .in(query[String]("y"))
        .out(plainBody[String])
        .serverLogic { token => y =>
          // original errors should work
          if (token != "secret") pureResult(FruitErrorDetail.Unknown(List("apple")).asLeft[String])
          // as well as new error variants
          else if (y == "1") pureResult(FruitErrorDetail.AlreadyPicked("1").asLeft[String])
          // and the success case
          else pureResult(s"ok $y".asRight[FruitErrorDetail])
        },
      "security error outputs extended with additional variants in main logic"
    ) { (backend, baseUri) =>
      basicRequest.get(uri"$baseUri?y=2").header("token", "secret").send(backend).map(_.body shouldBe Right("ok 2")) >>
        basicRequest.get(uri"$baseUri?y=2").header("token", "hacker").send(backend).map { r =>
          r.body.left.value should include("\"availableFruit\"")
        } >>
        basicRequest.get(uri"$baseUri?y=1").header("token", "secret").send(backend).map(_.body.left.value should include("\"name\""))
    },
    testServer(out_empty_or_default_json_output)((s: Int) =>
      pureResult {
        s match {
          case 1 => Right(CustomError.NotFound)
          case 2 => Right(CustomError.Default("unknown"))
        }
      }
    ) { (backend, baseUri) =>
      basicRequest.response(asStringAlways).get(uri"$baseUri/status?statusOut=1").send(backend).map { r =>
        r.code shouldBe StatusCode.NotFound
        r.body shouldBe ""
      } >> basicRequest.response(asStringAlways).get(uri"$baseUri/status?statusOut=2").send(backend).map { r =>
        r.code shouldBe StatusCode.BadRequest
        r.body shouldBe """{"msg":"unknown"}"""
      }
    },
    testServerLogic(
      endpoint
        .errorOut(
          oneOf[FruitErrorDetail](
            oneOfDefaultVariant(statusCode(StatusCode.InternalServerError).and(jsonBody[FruitErrorDetail.Unknown]))
          )
        )
        .in("test")
        .out(plainBody[String])
        .errorOutVariantsPrepend(
          oneOfVariant(StatusCode.Conflict, jsonBody[FruitErrorDetail.AlreadyPicked]),
          oneOfVariant(StatusCode.NotFound, jsonBody[FruitErrorDetail.NotYetGrown])
        )
        .serverLogic(_ => pureResult(FruitErrorDetail.NotYetGrown(7).asLeft[String])),
      ".errorOutVariantsPrepend variant takes precedence over devault .errorOut variant"
    ) { (backend, baseUri) =>
      basicRequest.response(asStringAlways).get(uri"$baseUri/test").send(backend).map { r =>
        r.code shouldBe StatusCode.NotFound
      }
    },
    testServerLogic(
      endpoint
        .errorOut(
          oneOf[FruitErrorDetail](
            oneOfDefaultVariant(statusCode(StatusCode.InternalServerError).and(jsonBody[FruitErrorDetail.Unknown]))
          )
        )
        .in("test")
        .out(plainBody[String])
        .errorOutVariantsPrepend(
          oneOfVariant(StatusCode.Conflict, jsonBody[FruitErrorDetail.AlreadyPicked]),
          oneOfVariant(StatusCode.NotFound, jsonBody[FruitErrorDetail.NotYetGrown])
        )
        .errorOutVariantsPrepend(
          oneOfVariant(StatusCode.BadRequest, jsonBody[FruitErrorDetail.AlreadyPicked])
        )
        .serverLogic(_ => pureResult(FruitErrorDetail.AlreadyPicked("cherry").asLeft[String])),
      "multiple .errorOutVariantsPrepend variants are executed in right order (a stack)"
    ) { (backend, baseUri) =>
      basicRequest.response(asStringAlways).get(uri"$baseUri/test").send(backend).map { r =>
        r.code shouldBe StatusCode.BadRequest
      }
    }
  )
}
