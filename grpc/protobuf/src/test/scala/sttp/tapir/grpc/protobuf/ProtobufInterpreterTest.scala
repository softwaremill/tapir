package sttp.tapir.grpc.protobuf

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import shapeless.PolyDefns.Compose.composeCase
import sttp.tapir.Schema.SName
import sttp.tapir.generic.Derived
import sttp.tapir._
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.grpc.protobuf.pbdirect._
import sttp.tapir.generic.auto._
import sttp.tapir.grpc.protobuf.ProtobufScalarType.{ProtobufInt32, ProtobufInt64, ProtobufString}
import sttp.tapir.grpc.protobuf.model.{ProtobufMessage, ProtobufMessageField}

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
    result.map(_.fields.map(field => (field.name, field.`type`))).head should contain theSameElementsAs List(
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
      ProtobufMessage(
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
      ProtobufMessage("B", List(ProtobufMessageField(ProtobufInt32, "int", None), ProtobufMessageField(ProtobufString, "s", None)))
    )
  }
}
