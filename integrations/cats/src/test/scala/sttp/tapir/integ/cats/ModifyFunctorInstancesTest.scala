package sttp.tapir.integ.cats

import org.scalatest.{Assertion, Inside}
import sttp.tapir.SchemaType._
import org.scalatest.flatspec.AnyFlatSpec
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.integ.cats.codec._
import org.scalatest.matchers.should.Matchers

class ModifyFunctorInstancesTest extends AnyFlatSpec with Matchers with ModifyFunctorInstances with Inside {

  it should "modify elements in NonEmptyList" in {
    implicitly[Schema[NonEmptyListWrapper]]
      .modify(_.f1.each)(_.format("xyz")) shouldContainACollectionElementWithSchema ("f1", Schema(SString()).format("xyz"))
  }

  it should "modify elements in NonEmptySet" in {
    implicitly[Schema[NonEmptySetWrapper]]
      .modify(_.f1.each)(_.format("xyz")) shouldContainACollectionElementWithSchema ("f1", Schema(SString()).format("xyz"))
  }

  it should "modify elements in Chain" in {
    implicitly[Schema[ChainWrapper]]
      .modify(_.f1.each)(_.format("xyz")) shouldContainACollectionElementWithSchema ("f1", Schema(SString()).format("xyz"))
  }

  it should "modify elements in NonEmptyChain" in {
    implicitly[Schema[NonEmptyChainWrapper]]
      .modify(_.f1.each)(_.format("xyz")) shouldContainACollectionElementWithSchema ("f1", Schema(SString()).format("xyz"))
  }

  implicit class CollectionSchemaMatcher[A](schema: Schema[A]) {
    def shouldContainACollectionElementWithSchema[B](fieldName: String, elemSchema: Schema[B]): Assertion =
      inside(schema.schemaType) {
        case SProduct(List(f)) if f.name.name == fieldName =>
          f.schema.schemaType shouldBe a[SArray[?, ?]]
          f.schema.schemaType.asInstanceOf[SArray[?, ?]].element shouldBe elemSchema
      }
  }
}
