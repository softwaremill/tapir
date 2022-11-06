package sttp.tapir.grpc.protobuf

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import shapeless.PolyDefns.Compose.composeCase
import sttp.tapir.Schema.SName
import sttp.tapir.generic.Derived
import sttp.tapir._
import pbdirect._
import _root_.pbdirect._
import com.google.protobuf.CodedOutputStream
import pbdirect._
import sttp.tapir.generic.auto._
import sttp.tapir.grpc.protobuf.ProtobufScalarType.{ProtobufInt32, ProtobufInt64, ProtobufString}
import sttp.tapir.grpc.protobuf.model.{ProtobufMessage, ProtobufMessageField, ProtobufProductMessage}

class ProtobufInterpreterTest extends AnyFlatSpec with Matchers {
  val endpointToProtobufMessage = new EndpointToProtobufMessage()
  val interpreter = new ProtobufInterpreter(new EndpointToProtobufMessage(), new EndpointToProtobufService())
  val baseTestEndpoint = endpoint

  it should "handle all scalar types" in {
    case class TestClass(
        int: Int,
        long: Long,
        string: String,
        float: Float,
        double: Double,
        byte: Byte,
        boolean: Boolean,
        short: Short,
        unit: Unit
    )

    val testEndpoint = baseTestEndpoint.in(grpcBody[TestClass])

    val result = endpointToProtobufMessage(List(testEndpoint))

    result.head.name shouldBe "TestClass"
    result.head.asInstanceOf[ProtobufProductMessage].fields.map(field => (field.name, field.`type`)) should contain theSameElementsAs List(
      "int" -> ProtobufScalarType.ProtobufInt32,
      "long" -> ProtobufScalarType.ProtobufInt64,
      "string" -> ProtobufScalarType.ProtobufString,
      "float" -> ProtobufScalarType.ProtobufFloat,
      "double" -> ProtobufScalarType.ProtobufDouble,
      "byte" -> ProtobufScalarType.ProtobufInt32,
      "boolean" -> ProtobufScalarType.ProtobufBool,
      "short" -> ProtobufScalarType.ProtobufInt32,
      "unit" -> ProtobufScalarType.ProtobufEmpty
    )
  }

  it should "use ref for product fields" in {
    case class B(int: Int, s: String)
    case class A(l: Long, b: B)

    val testEndpoint = baseTestEndpoint.in(grpcBody[A])

    val result = endpointToProtobufMessage(List(testEndpoint))

    result should contain theSameElementsAs List(
      ProtobufProductMessage(
        "A",
        List(
          ProtobufMessageField(ProtobufInt64, "l", None),
          ProtobufMessageField(
            ProtobufMessageRef(SName("sttp.tapir.grpc.protobuf.ProtobufInterpreterTest.<local ProtobufInterpreterTest>.B")),
            "b",
            None
          )
        )
      ),
      ProtobufProductMessage("B", List(ProtobufMessageField(ProtobufInt32, "int", None), ProtobufMessageField(ProtobufString, "s", None)))
    )
  }

  it should "handle collections of scalars" in {
    implicit def iterableDummyWriter[A, F[_] <: Iterable[_]]: PBMessageWriter[F[A]] = (value: F[A], out: CodedOutputStream) => ???
    implicit def iterableDummyReader[A, F[_] <: Iterable[_]]: PBMessageReader[F[A]] = (bytes: Array[Byte]) => ???
    case class TestClass(
        li: List[Int],
        vi: Vector[Int],
        si: Set[Int]
    )

    val testEndpoint = baseTestEndpoint.in(grpcBody[TestClass])

    val result = endpointToProtobufMessage(List(testEndpoint))

    result.head.name shouldBe "TestClass"
    result.head.asInstanceOf[ProtobufProductMessage].fields.map(field => (field.name, field.`type`)) should contain theSameElementsAs List(
      "li" -> ProtobufRepeatedField(ProtobufInt32),
      "vi" -> ProtobufRepeatedField(ProtobufInt32),
      "si" -> ProtobufRepeatedField(ProtobufInt32)
    )
  }

  it should "handle collection of messages" in {

    case class A()
    case class TestClass(
        la: List[A]
    )

    val testEndpoint = baseTestEndpoint.in(grpcBody[TestClass])

    val result = endpointToProtobufMessage(List(testEndpoint))

    result.map(_.name) should contain theSameElementsAs List("TestClass", "A")
    result.flatMap(_.asInstanceOf[ProtobufProductMessage].fields.map(field => (field.name, field.`type`))) should contain theSameElementsAs List(
      "la" -> ProtobufRepeatedField(
        ProtobufMessageRef(SName("sttp.tapir.grpc.protobuf.ProtobufInterpreterTest.<local ProtobufInterpreterTest>.A"))
      )
    )
  }

  it should "allow to customize fields types" in {
    case class TestClass(
        x: Int
    )

    implicit val testClassSchema: Typeclass[TestClass] =
      implicitly[Derived[Schema[TestClass]]].value.modify(_.x)(_.attribute(ProtobufAttributes.ScalarValueAttribute, ProtobufScalarType.ProtobufString))

    val testEndpoint = baseTestEndpoint.in(grpcBody[TestClass])

    val result = endpointToProtobufMessage(List(testEndpoint))

    result.head.name should contain theSameElementsAs "TestClass"
    result.head.asInstanceOf[ProtobufProductMessage].fields.map(field => (field.name, field.`type`)) should contain theSameElementsAs List(
      "x" -> ProtobufScalarType.ProtobufString
    )
  }

  it should "fail on nested sequences" in {
    implicit val anyDummyReader: PBMessageReader[List[List[Int]]] = (bytes: Array[Byte]) => List.empty
    implicit val anyDummyWriter: PBMessageWriter[List[List[Int]]] = (value: List[List[Int]], out: CodedOutputStream) => ()
    case class TestClass(
        llia: List[List[Int]]
    )

    val testEndpoint = baseTestEndpoint.in(grpcBody[TestClass])

    assertThrows[IllegalArgumentException](endpointToProtobufMessage(List(testEndpoint)))
  }
}

object ProtobufInterpreterTest {
  private implicit def iterableDummyWriter[A, F[_] <: Iterable[_]]: PBMessageWriter[F[A]] = (value: F[A], out: CodedOutputStream) => ???
  private implicit def iterableDummyReader[A, F[_] <: Iterable[_]]: PBMessageReader[F[A]] = (bytes: Array[Byte]) => ???

}
