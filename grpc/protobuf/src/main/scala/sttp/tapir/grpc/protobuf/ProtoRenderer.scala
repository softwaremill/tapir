package sttp.tapir.grpc.protobuf

import sttp.tapir.grpc.protobuf.model._

import java.util.concurrent.atomic.AtomicInteger

class ProtoRenderer {
  def render(protobuf: Protobuf): String = {
    s"""
      |${ProtoRenderer.header}
      |${renderOptions(protobuf.options)}
      |
      |${protobuf.services.map(renderService).mkString("\n")}
      |
      |${protobuf.messages.map(renderMessage).mkString("\n\n")}
      """.stripMargin
  }

  private def renderService(service: ProtobufService): String =
    s"""
    |service ${service.name} {
    |${service.methods.map(renderMethod).mkString("\n")}
    |}
    """.stripMargin

  private def renderMethod(method: ProtobufServiceMethod): String =
    s"""
    |rpc ${method.name} (${method.input}) returns (${method.output}) {}
    """.stripMargin

  private def renderMessage(msg: ProtobufMessage): String = {
    s"""
       |message ${msg.name} {
       |${renderMessageFields(msg.fields.toVector)}

       |}
        """.stripMargin
  }

  private def renderOptions(options: ProtobufOptions): String =
    options.maybePackageName.map(renderPackageName).getOrElse("")

  private def renderPackageName(packageName: PackageName): String =
    s"""
         |option java_package = "$packageName";
         |""".stripMargin

  // I use here Vector because of the `lastOption` operation, consider if it make sense
  private def renderMessageFields(fields: Vector[ProtobufMessageField]): String = {

    val fieldsWithDefinedId = fields
      .flatMap {
        case pmf @ ProtobufMessageField(_, _, Some(id)) => Some((id, pmf))
        case _                                          => None
      }
      .sortBy(_._1)
    val fieldsWithUndefinedId = fields.filter(_.maybeId.isEmpty)

    val lastUsedId = {
      val id = fieldsWithDefinedId.lastOption.map(_._1).getOrElse(0)
      new AtomicInteger(id)
    }

    val renderedFields =
      fieldsWithDefinedId
        .map { case (id, field) => renderMessageField(field, id) } ++ fieldsWithUndefinedId.map(field =>
        renderMessageField(field, lastUsedId.incrementAndGet())
      )

    renderedFields.mkString("\n")
  }

  private def renderMessageField(field: ProtobufMessageField, id: Int): String =
    s"""
        |${field.`type`} ${field.name} = $id;
        """.stripMargin

}

object ProtoRenderer {
  private val header =
    s"""
        |syntax = "proto3";
        |
        |option java_multiple_files = true;
        |""".stripMargin
}
