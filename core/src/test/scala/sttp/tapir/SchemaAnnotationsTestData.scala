package sttp.tapir

import sttp.tapir.Schema.annotations.{customise, default, description, encodedExample, encodedName, format, hidden, validate}

object SchemaAnnotationsTestData {
  @description("my-string")
  @encodedExample("encoded-example")
  @default(MyString("default"), encoded = Some("encoded-default"))
  @format("utf8")
  @Schema.annotations.deprecated
  @encodedName("encoded-name")
  @validate(Validator.pass[MyString])
  @hidden
  case class MyString(value: String)

  @format("utf8")
  @customise(_.format("c1"))
  @customise(s => s.format(s.format.getOrElse("") + "-c2"))
  case class MyCustomisedString(value: String)
}
