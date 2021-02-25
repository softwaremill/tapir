package sttp.tapir.integ.cats

import cats.data._
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
      .modify(_.f1.each)(_.format("xyz")) shouldContainACollectionElementWithSchema ("f1", Schema(SString).format("xyz"))
  }

  it should "modify elements in NonEmptySet" in {
    implicitly[Typeclass[NonEmptySetWrapper]]
      .modify(_.f1.each)(_.format("xyz")) shouldContainACollectionElementWithSchema ("f1", Schema(SString).format("xyz"))
  }

  it should "modify elements in Chain" in {
    implicitly[Schema[ChainWrapper]]
      .modify(_.f1.each)(_.format("xyz")) shouldContainACollectionElementWithSchema ("f1", Schema(SString).format("xyz"))
  }

  it should "modify elements in NonEmptyChain" in {
    implicitly[Schema[NonEmptyChainWrapper]]
      .modify(_.f1.each)(_.format("xyz")) shouldContainACollectionElementWithSchema ("f1", Schema(SString).format("xyz"))
  }

  implicit class CollectionSchemaMatcher[A](schema: Schema[A]) {
    def shouldContainACollectionElementWithSchema[B](fieldName: String, elemSchema: Schema[B]): Assertion =
      inside(schema.schemaType) {
        case SProduct(_, List((FieldName(name, _), Schema(SArray(s), _, _, _, _, _, _, _)))) if name == fieldName =>
          s shouldBe (elemSchema)
      }
  }
}

case class NonEmptyListWrapper(f1: NonEmptyList[String])
case class NonEmptySetWrapper(f1: NonEmptySet[String])
case class ChainWrapper(f1: Chain[String])
case class NonEmptyChainWrapper(f1: NonEmptyChain[String])
