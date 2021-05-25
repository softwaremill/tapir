package sttp.tapir

// class LegacySchemaApplyValidationTest extends AnyFlatSpec with Matchers {
//   import SchemaApplyValidationTestData._

//     it should "validate recursive values" in {
//     import sttp.tapir.generic.auto._
//     implicit val stringSchema: Schema[String] = Schema.schemaForString.validate(Validator.minLength(1))
//     val schema: Schema[RecursiveName] = implicitly[Schema[RecursiveName]]

//     schema.applyValidation(RecursiveName("x", None)) shouldBe Nil
//     schema.applyValidation(RecursiveName("", None)) shouldBe List(
//       ValidationError.Primitive(Validator.minLength(1), "", List(FieldName("name")))
//     )
//     schema.applyValidation(RecursiveName("x", Some(Vector(RecursiveName("x", None))))) shouldBe Nil
//     schema.applyValidation(RecursiveName("x", Some(Vector(RecursiveName("", None))))) shouldBe List(
//       ValidationError.Primitive(Validator.minLength(1), "", List(FieldName("subNames"), FieldName("name")))
//     )
//     schema.applyValidation(RecursiveName("x", Some(Vector(RecursiveName("x", Some(Vector(RecursiveName("x", None)))))))) shouldBe Nil
//     schema.applyValidation(RecursiveName("x", Some(Vector(RecursiveName("x", Some(Vector(RecursiveName("", None)))))))) shouldBe List(
//       ValidationError.Primitive(Validator.minLength(1), "", List(FieldName("subNames"), FieldName("subNames"), FieldName("name")))
//     )
//   }

//   it should "show recursive validators" in {
//     import sttp.tapir.generic.auto._
//     implicit val stringSchema: Schema[String] = Schema.schemaForString.validate(Validator.minLength(1))
//     val s: Schema[RecursiveName] = implicitly[Schema[RecursiveName]]
//     s.showValidators shouldBe Some("name->(length>=1),subNames->(elements(elements(recursive)))")
//   }

//   #946: derivation of validator twice in the same derivation
//   it should "validate recursive values with two-level hierarchy" in {
//     import sttp.tapir.generic.auto._
//     implicit val stringSchema: Schema[String] = Schema.schemaForString.validate(Validator.minLength(1))
//     val schema: Schema[Animal] = implicitly[Schema[Animal]]

//     schema.applyValidation(Dog("reksio1", Nil)) shouldBe Nil
//     schema.applyValidation(Dog("", Nil)) should have length 1
//     schema.applyValidation(Dog("reksio1", List(Dog("reksio2", Nil)))) shouldBe Nil
//     schema.applyValidation(Dog("reksio1", List(Dog("", Nil)))) should have length 1
//     schema.applyValidation(Dog("reksio1", List(Dog("reksio2", List(Dog("reksio3", Nil)))))) shouldBe Nil
//     schema.applyValidation(Dog("reksio1", List(Dog("reksio2", List(Dog("", Nil)))))) should have length 1

//     schema.applyValidation(Cat("tom1", Nil)) shouldBe Nil
//     schema.applyValidation(Cat("", Nil)) should have length 1
//     schema.applyValidation(Cat("tom1", List(Cat("tom2", Nil)))) shouldBe Nil
//     schema.applyValidation(Cat("tom1", List(Cat("", Nil)))) should have length 1
//     schema.applyValidation(Cat("tom1", List(Cat("tom2", List(Cat("tom3", Nil)))))) shouldBe Nil
//     schema.applyValidation(Cat("tom1", List(Cat("tom2", List(Cat("", Nil)))))) should have length 1

//     schema.applyValidation(Dog("reksio1", List(Dog("reksio2", List(Cat("tom3", Nil)))))) shouldBe Nil
//     schema.applyValidation(Dog("reksio1", List(Dog("reksio2", List(Cat("", Nil)))))) should have length 1

//     schema.applyValidation(Cat("tom1", List(Cat("tom2", List(Dog("reksio3", Nil)))))) shouldBe Nil
//     schema.applyValidation(Cat("tom1", List(Cat("tom2", List(Dog("", Nil)))))) should have length 1
//   }
// }
