package sttp.tapir.grpc.protobuf

import org.scalatest.flatspec.AnyFlatSpec
import sttp.tapir.Schema.SName
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
      messages =
        List(ProtobufProductMessage("SimpleBook", List(ProtobufMessageField(ProtobufScalarType.ProtobufString, "title", Some(1))))),
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
      messages =
        List(ProtobufProductMessage("SimpleBook", List(ProtobufMessageField(ProtobufScalarType.ProtobufString, "title", Some(1))))),
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

  it should "render proto file for a message with repeated values" in {
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
        |   repeated string authors = 3;
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
            ProtobufMessageField(ProtobufScalarType.ProtobufString, "content", Some(2)),
            ProtobufMessageField(ProtobufRepeatedField(ProtobufScalarType.ProtobufString), "authors", Some(3))
          )
        )
      ),
      services = List(ProtobufService("Library", List(ProtobufServiceMethod("AddBook", "Title", "SimpleBook")))),
      options = ProtobufOptions.empty
    )

    matchProtos(renderer.render(proto), expectedProto)
  }

  it should "render proto file for a message with a coproduct field" in {
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
        |message Epub {
        |   string source = 1;
        |}
        |
        |message Paper {
        |   string size = 1;
        |}
        |message Format {
        |   oneof alternatives {
        |     Epub epub = 1;
        |     Paper paper = 2;
        |   }
        |}
        |
        |message SimpleBook {
        |   string title = 1;
        |   string content = 2;
        |   Format format = 3;
        |
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
          "Epub",
          List(
            ProtobufMessageField(ProtobufScalarType.ProtobufString, "source", Some(1))
          )
        ),
        ProtobufProductMessage(
          "Paper",
          List(
            ProtobufMessageField(ProtobufScalarType.ProtobufString, "size", Some(1))
          )
        ),
        ProtobufCoproductMessage(
          "Format",
          List(
            ProtobufMessageField(ProtobufMessageRef(SName("Epub")), "epub", None),
            ProtobufMessageField(ProtobufMessageRef(SName("Paper")), "paper", None)
          )
        ),
        ProtobufProductMessage(
          "SimpleBook",
          List(
            ProtobufMessageField(ProtobufScalarType.ProtobufString, "title", Some(1)),
            ProtobufMessageField(ProtobufScalarType.ProtobufString, "content", Some(2)),
            ProtobufMessageField(ProtobufMessageRef(SName("Format")), "format", Some(3))
          )
        )
      ),
      services = List(ProtobufService("Library", List(ProtobufServiceMethod("AddBook", "Title", "SimpleBook")))),
      options = ProtobufOptions.empty
    )

    println(renderer.render(proto))
    matchProtos(renderer.render(proto), expectedProto)
  }
}
