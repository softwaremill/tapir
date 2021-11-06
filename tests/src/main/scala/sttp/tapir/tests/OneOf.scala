package sttp.tapir.tests

import io.circe.generic.auto._
import sttp.model.StatusCode
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.tests.data.{Entity, FruitErrorDetail, Organization, Person}
import sttp.tapir._

object OneOf {
  val in_string_out_status_from_string: PublicEndpoint[String, Unit, Either[Int, String], Any] =
    endpoint
      .in(query[String]("fruit"))
      .out(
        oneOf[Either[Int, String]](
          oneOfMappingValueMatcher(StatusCode.Accepted, plainBody[Int].map(Left(_))(_.value)) { case Left(_: Int) => true },
          oneOfMappingValueMatcher(StatusCode.Ok, plainBody[String].map(Right(_))(_.value)) { case Right(_: String) => true }
        )
      )

  val in_int_out_value_form_exact_match: PublicEndpoint[Int, Unit, String, Any] =
    endpoint
      .in("mapping")
      .in(query[Int]("num"))
      .out(
        oneOf(
          oneOfMappingExactMatcher(StatusCode.Accepted, plainBody[String])("A"),
          oneOfMappingExactMatcher(StatusCode.Ok, plainBody[String])("B")
        )
      )

  val in_string_out_status_from_type_erasure_using_partial_matcher: PublicEndpoint[String, Unit, Option[Either[Int, String]], Any] = {
    import sttp.tapir.typelevel.MatchType

    endpoint
      .in(query[String]("fruit"))
      .out(
        oneOf[Option[Either[Int, String]]](
          oneOfMapping(StatusCode.NoContent, emptyOutput.map[None.type]((_: Unit) => None)(_ => ())),
          oneOfMappingValueMatcher(StatusCode.Accepted, jsonBody[Some[Left[Int, String]]])(
            implicitly[MatchType[Some[Left[Int, String]]]].partial
          ),
          oneOfMappingValueMatcher(StatusCode.Ok, jsonBody[Some[Right[Int, String]]])(
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
          oneOfMappingValueMatcher(StatusCode.Accepted, emptyOutput.map(Left(_))(_.value)) { case Left(_: Unit) => true },
          oneOfMappingValueMatcher(StatusCode.Ok, plainBody[String].map(Right(_))(_.value)) { case Right(_: String) => true }
        )
      )

  val out_json_or_default_json: PublicEndpoint[String, Unit, Entity, Any] =
    endpoint.get
      .in("entity" / path[String]("type"))
      .out(
        oneOf[Entity](
          oneOfMapping[Person](StatusCode.Created, jsonBody[Person]),
          oneOfDefaultMapping[Organization](jsonBody[Organization])
        )
      )

  val out_no_content_or_ok_empty_output: PublicEndpoint[Int, Unit, Unit, Any] = {
    val anyMatches: PartialFunction[Any, Boolean] = { case _ => true }

    endpoint
      .in("status")
      .in(query[Int]("statusOut"))
      .out(
        oneOf(
          oneOfMappingValueMatcher(StatusCode.NoContent, emptyOutput)(anyMatches),
          oneOfMappingValueMatcher(StatusCode.Ok, emptyOutput)(anyMatches)
        )
      )
  }

  val out_json_or_empty_output_no_content: PublicEndpoint[Int, Unit, Either[Unit, Person], Any] =
    endpoint
      .in("status")
      .in(query[Int]("statusOut"))
      .out(
        oneOf[Either[Unit, Person]](
          oneOfMappingValueMatcher(StatusCode.NoContent, jsonBody[Person].map(Right(_))(_ => Person("", 0))) { case Person(_, _) => true },
          oneOfMappingValueMatcher(StatusCode.NoContent, emptyOutput.map(Left(_))(_ => ())) { case () => true }
        )
      )

  val in_string_out_error_detail_nested: PublicEndpoint[String, FruitErrorDetail, Unit, Any] =
    endpoint
      .in(query[String]("fruit"))
      .errorOut(
        oneOf[FruitErrorDetail](
          oneOfMapping(
            oneOf[FruitErrorDetail.HarvestError](
              oneOfMapping(jsonBody[FruitErrorDetail.AlreadyPicked]),
              oneOfMapping(jsonBody[FruitErrorDetail.NotYetGrown])
            )
          ),
          oneOfMapping(jsonBody[FruitErrorDetail.NameTooShort]),
          oneOfMapping(jsonBody[FruitErrorDetail.Unknown])
        )
      )
}
