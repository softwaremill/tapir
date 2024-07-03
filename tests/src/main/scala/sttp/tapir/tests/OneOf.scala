package sttp.tapir.tests

import io.circe.generic.auto._
import sttp.model.StatusCode
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.tests.data.{CustomError, Entity, FruitErrorDetail, Organization, Person}
import sttp.tapir._

object OneOf {
  val in_string_out_status_from_string: PublicEndpoint[String, Unit, Either[Int, String], Any] =
    endpoint
      .in(query[String]("fruit"))
      .out(
        oneOf[Either[Int, String]](
          oneOfVariantValueMatcher(StatusCode.Accepted, plainBody[Int].map(Left(_))(_.value)) { case Left(_: Int) => true },
          oneOfVariantValueMatcher(StatusCode.Ok, plainBody[String].map(Right(_))(_.value)) { case Right(_: String) => true }
        )
      )

  val in_int_out_value_form_exact_match: PublicEndpoint[Int, Unit, String, Any] =
    endpoint
      .in("mapping")
      .in(query[Int]("num"))
      .out(
        oneOf(
          oneOfVariantExactMatcher(StatusCode.Accepted, plainBody[String])("A"),
          oneOfVariantExactMatcher(StatusCode.Ok, plainBody[String])("B")
        )
      )

  val in_string_out_status_from_type_erasure_using_partial_matcher: PublicEndpoint[String, Unit, Option[Either[Int, String]], Any] = {
    import sttp.tapir.typelevel.MatchType

    endpoint
      .in(query[String]("fruit"))
      .out(
        oneOf[Option[Either[Int, String]]](
          oneOfVariant(StatusCode.NoContent, emptyOutput.map[None.type]((_: Unit) => None)(_ => ())),
          oneOfVariantValueMatcher(StatusCode.Accepted, jsonBody[Some[Left[Int, String]]])(
            implicitly[MatchType[Some[Left[Int, String]]]].partial
          ),
          oneOfVariantValueMatcher(StatusCode.Ok, jsonBody[Some[Right[Int, String]]])(
            implicitly[MatchType[Some[Right[Int, String]]]].partial
          )
        )
      )
  }
  val in_string_out_status_from_string_one_empty: PublicEndpoint[String, Unit, Either[Unit, String], Any] =
    endpoint
      .in(query[String]("fruit"))
      .out(
        oneOf[Either[Unit, String]](
          oneOfVariantValueMatcher(StatusCode.Accepted, emptyOutput.map(Left(_))(_.value)) { case Left(_: Unit) => true },
          oneOfVariantValueMatcher(StatusCode.Ok, plainBody[String].map(Right(_))(_.value)) { case Right(_: String) => true }
        )
      )

  val out_json_or_default_json: PublicEndpoint[String, Unit, Entity, Any] =
    endpoint.get
      .in("entity" / path[String]("type"))
      .out(
        oneOf[Entity](
          oneOfVariant[Person](StatusCode.Created, jsonBody[Person]),
          oneOfDefaultVariant[Organization](jsonBody[Organization])
        )
      )

  val out_no_content_or_ok_empty_output: PublicEndpoint[Int, Unit, Unit, Any] = {
    val anyMatches: PartialFunction[Any, Boolean] = { case _ => true }

    endpoint
      .in("status")
      .in(query[Int]("statusOut"))
      .out(
        oneOf(
          oneOfVariantValueMatcher(StatusCode.NoContent, emptyOutput)(anyMatches),
          oneOfVariantValueMatcher(StatusCode.Ok, emptyOutput)(anyMatches)
        )
      )
  }

  val out_json_or_empty_output_no_content: PublicEndpoint[Int, Unit, Either[Unit, Person], Any] =
    endpoint
      .in("status")
      .in(query[Int]("statusOut"))
      .out(
        oneOf[Either[Unit, Person]](
          oneOfVariantValueMatcher(StatusCode.NoContent, jsonBody[Person].map(Right(_))(_ => Person("", 0))) { case Right(_) => true },
          oneOfVariantValueMatcher(StatusCode.NoContent, emptyOutput.map(Left(_))(_ => ())) { case Left(_) => true }
        )
      )

  val out_empty_or_default_json_output: PublicEndpoint[Int, Unit, CustomError, Any] =
    endpoint
      .in("status")
      .in(query[Int]("statusOut"))
      .out(
        oneOf[CustomError](
          oneOfVariant(StatusCode.NotFound, emptyOutputAs(CustomError.NotFound)),
          oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[CustomError.Default]))
        )
      )

  val in_string_out_error_detail_nested: PublicEndpoint[String, FruitErrorDetail, Unit, Any] =
    endpoint
      .in(query[String]("fruit"))
      .errorOut(
        oneOf[FruitErrorDetail](
          oneOfVariant(
            oneOf[FruitErrorDetail.HarvestError](
              oneOfVariant(jsonBody[FruitErrorDetail.AlreadyPicked]),
              oneOfVariant(jsonBody[FruitErrorDetail.NotYetGrown])
            )
          ),
          oneOfVariant(jsonBody[FruitErrorDetail.NameTooShort]),
          oneOfVariant(jsonBody[FruitErrorDetail.Unknown])
        )
      )

  val out_status_or_status_with_body: PublicEndpoint[String, FruitErrorDetail, Unit, Any] =
    endpoint
      .in(query[String]("fruit"))
      .errorOut(
        oneOf[FruitErrorDetail](
          // the server returns various errors for status code 400, but here we always map to this one, disregarding the actual body
          oneOfVariant(statusCode(StatusCode.BadRequest).map(_ => FruitErrorDetail.NameTooShort(10))(_ => ())),
          oneOfVariant(statusCode(StatusCode.NotFound).and(jsonBody[FruitErrorDetail.Unknown]))
        )
      )

  val in_int_out_value_form_singleton: PublicEndpoint[Int, Unit, String, Any] =
    endpoint
      .in("mapping")
      .in(query[Int]("num"))
      .out(
        oneOf(
          oneOfVariantSingletonMatcher(StatusCode.Accepted)("A"),
          oneOfVariantSingletonMatcher(statusCode(StatusCode.Ok))("B")
        )
      )
}
