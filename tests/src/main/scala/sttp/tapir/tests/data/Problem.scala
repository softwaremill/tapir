package sttp.tapir.tests.data

import sttp.tapir.{Schema, SchemaType}

case class Problem()

object Problem {
  implicit val schema: Schema[Problem] =
    Schema[Problem](
      SchemaType.SRef(
        Schema.SName("https://opensource.zalando.com/restful-api-guidelines/models/problem-1.0.1.yaml#/Problem")
      )
    )
}
