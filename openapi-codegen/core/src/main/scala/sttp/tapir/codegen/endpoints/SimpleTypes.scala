package sttp.tapir.codegen.endpoints

import sttp.tapir.codegen.openapi.models.OpenapiSchemaType._
import sttp.tapir.codegen.util.NameValidation

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
        // The ref target is spliced raw as a type identifier here, and this is the single choke point through which
        // every $ref (in component schemas, parameters — method- and path-level, resolved or component-indirected —
        // request/response bodies, and response headers) becomes a type. Validate it here rather than relying on
        // every document location being enumerated by NameValidation. See GHSA-gpcc-36pq-8qxr.
        val name = t.split('/').last
        NameValidation.validateName("schema $ref", name)
        (name, false)
      case x => throw new NotImplementedError(s"Not all simple types supported! Found $x")
    }
  }
}
