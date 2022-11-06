package sttp.tapir.grpc.protobuf

import org.scalatest.flatspec.AnyFlatSpec
import sttp.tapir.grpc.protobuf.model._

class ProtoRendererTest extends AnyFlatSpec with ProtobufMatchers {
  val renderer = new ProtoRenderer

  it should "render proto file for a simple service" in {
    val expectedProto =
      """
        |syntax = "proto3";
        |
        |option java_multiple_files = true;
        |
        |service Library {
        |   rpc AddBook (SimpleBook) returns (SimpleBook) {} 
        |}
        |
        |message SimpleBook {
        |   string title = 1;
        |}
        |""".stripMargin

    val proto = Protobuf(
      messages = List(ProtobufProductMessage("SimpleBook", List(ProtobufMessageField(ProtobufScalarType.ProtobufString, "title", Some(1))))),
      services = List(ProtobufService("Library", List(ProtobufServiceMethod("AddBook", "SimpleBook", "SimpleBook")))),
      options = ProtobufOptions.empty
    )

    matchProtos(renderer.render(proto), expectedProto)
  }

  it should "render proto options" in {
    val expectedProto =
      """
        |syntax = "proto3";
        |
        |option java_multiple_files = true;
        |option java_package = "com.myexample";
        |
        |service Library {
        |   rpc AddBook (SimpleBook) returns (SimpleBook) {}
        |}
        |
        |message SimpleBook {
        |   string title = 1;
        |}
        |""".stripMargin

    val proto = Protobuf(
      messages = List(ProtobufProductMessage("SimpleBook", List(ProtobufMessageField(ProtobufScalarType.ProtobufString, "title", Some(1))))),
      services = List(ProtobufService("Library", List(ProtobufServiceMethod("AddBook", "SimpleBook", "SimpleBook")))),
      options = ProtobufOptions(Some("com.myexample"))
    )

    matchProtos(renderer.render(proto), expectedProto)
  }

  it should "render proto file for a different input and output" in {
    val expectedProto =
      """
        |syntax = "proto3";
        |
        |option java_multiple_files = true;
        |
        |service Library {
        |   rpc AddBook (Title) returns (SimpleBook) {} 
        |}
        |
        |message Title {
        |   string title = 1;
        |}
        |
        |message SimpleBook {
        |   string title = 1;
        |   string content = 2;
        |}
        |""".stripMargin

    val proto = Protobuf(
      messages = List(
        ProtobufProductMessage(
          "Title",
          List(
            ProtobufMessageField(ProtobufScalarType.ProtobufString, "title", Some(1))
          )
        ),
        ProtobufProductMessage(
          "SimpleBook",
          List(
            ProtobufMessageField(ProtobufScalarType.ProtobufString, "title", Some(1)),
            ProtobufMessageField(ProtobufScalarType.ProtobufString, "content", Some(2))
          )
        )
      ),
      services = List(ProtobufService("Library", List(ProtobufServiceMethod("AddBook", "Title", "SimpleBook")))),
      options = ProtobufOptions.empty
    )

    matchProtos(renderer.render(proto), expectedProto)
  }

}
