package sttp.tapir.codegen.endpoints

import sttp.tapir.codegen.openapi.models.OpenapiSchemaType._

object SimpleTypes {

  def mapSchemaSimpleTypeToType(osst: OpenapiSchemaSimpleType, multipartForm: Boolean = false): (String, Boolean) = {
    osst match {
      case OpenapiSchemaDouble(nb, _) =>
        ("Double", nb)
      case OpenapiSchemaFloat(nb, _) =>
        ("Float", nb)
      case OpenapiSchemaInt(nb, _) =>
        ("Int", nb)
      case OpenapiSchemaLong(nb, _) =>
        ("Long", nb)
      case OpenapiSchemaDate(nb) =>
        ("java.time.LocalDate", nb)
      case OpenapiSchemaDateTime(nb) =>
        ("java.time.Instant", nb)
      case OpenapiSchemaDuration(nb) =>
        ("java.time.Duration", nb)
      case OpenapiSchemaUUID(nb) =>
        ("java.util.UUID", nb)
      case OpenapiSchemaString(nb, _, _, _) =>
        ("String", nb)
      case OpenapiSchemaBoolean(nb) =>
        ("Boolean", nb)
      case OpenapiSchemaBinary(nb) if multipartForm =>
        ("sttp.model.Part[java.io.File]", nb)
      case OpenapiSchemaBinary(nb) =>
        ("Array[Byte]", nb)
      case OpenapiSchemaByte(nb) =>
        ("ByteString", nb)
      case OpenapiSchemaAny(nb, t) =>
        (AnyType.toCirceTpe(t), nb)
      case OpenapiSchemaRef(t) =>
        (t.split('/').last, false)
      case x => throw new NotImplementedError(s"Not all simple types supported! Found $x")
    }
  }
}
