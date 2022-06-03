package sttp.tapir

import sttp.tapir.Schema.annotations.{default, description, encodedExample, encodedName, format, validate}

object SchemaAnnotationsTestData {
  @description("my-string")
  @encodedExample("encoded-example")
  @default(MyString("default"), encoded = Some("encoded-default"))
  @format("utf8")
  @Schema.annotations.deprecated
  @encodedName("encoded-name")
  @validate(Validator.pass[MyString])
  case class MyString(value: String)
}
