package sttp.tapir.codegen.util

import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType._

/** Ingestion-time validation of names taken from an (untrusted) OpenAPI document that the code generator emits into
  * *identifier* positions — component schema names, `$ref` targets, object property names and (when used for object
  * names) tags. These become class/trait/type/field/val names in the generated source, frequently by raw string
  * concatenation (e.g. `${name.capitalize}Decoder`) rather than backtick-quoting, so a name containing characters
  * outside the OpenAPI-permitted identifier set could inject arbitrary Scala code.
  *
  * We restrict them to the character set OpenAPI itself permits for component names
  * (https://spec.openapis.org/oas/v3.1.0#components-object), which contains no character able to form executable
  * Scala. Values that are emitted as string literals instead (parameter names, URLs, descriptions, default values,
  * discriminator values, enum values, XML names) can legitimately contain other characters and are escaped at their
  * emission site rather than restricted here. See GHSA-gpcc-36pq-8qxr.
  */
object NameValidation {

  private val SafeNamePattern = "[A-Za-z0-9._-]+"

  private def check(kind: String, name: String): Unit =
    if (!name.matches(SafeNamePattern))
      throw new IllegalArgumentException(
        s"Unsafe $kind '$name' in OpenAPI document: only characters [A-Za-z0-9._-] are permitted (see GHSA-gpcc-36pq-8qxr)"
      )

  // Collect (kind, name) pairs for every $ref target and (identifier-emitting) property name reachable from a schema.
  private def namesIn(schema: OpenapiSchemaType): Seq[(String, String)] = schema match {
    case r: OpenapiSchemaRef                 => Seq("schema $ref" -> r.stripped)
    case OpenapiSchemaArray(i, _, _, _)      => namesIn(i)
    case OpenapiSchemaMap(i, _, _)           => namesIn(i)
    case OpenapiSchemaNot(i)                 => namesIn(i)
    case OpenapiSchemaObject(props, _, _, _) =>
      // Every property name can reach a *raw* (string-concatenated, non-backtick-quoted) identifier position under
      // some feature: nested class/enum names via `addName` (object/array/map/enum properties), derived codec `val`
      // names via `${n.capitalize}` in the XML serde generator ($ref/array properties), and per-field validator `val`
      // names in ValidationGenerator (scalar properties with a restriction). Only the plain case-class FIELD position
      // is backtick-quoted and tolerant. Rather than enumerate which property types reach which raw sink — a census
      // that has repeatedly proven incomplete — restrict EVERY property name. This is a deliberate, fail-closed
      // over-approximation: a name that would in fact sanitise to a valid identifier (e.g. "@odata.type", "first
      // name") is rejected rather than accepted. See GHSA-gpcc-36pq-8qxr.
      props.toSeq.flatMap { case (propName, field) => ("property name" -> propName) +: namesIn(field.`type`) }
    case OpenapiSchemaOneOf(types, _) => types.flatMap(namesIn)
    case OpenapiSchemaAnyOf(types)    => types.flatMap(namesIn)
    case OpenapiSchemaAllOf(types)    => types.flatMap(namesIn)
    // Remaining variants are leaf simple types (validated as $refs above where relevant) with no nested names.
    case _ => Nil
  }

  /** Validate every name in the document that reaches an identifier position. Throws IllegalArgumentException on the
    * first unsafe name. Idempotent and cheap, so it is safe to call from each public generator entry point.
    */
  def validateDocumentNames(doc: OpenapiDocument, useHeadTagForObjectNames: Boolean): Unit = {
    val schemas = doc.components.toSeq.flatMap(_.schemas)
    schemas.foreach { case (name, _) => check("schema name", name) }
    // $ref targets between component schemas become type references; property names become field/derived identifiers.
    // Endpoint request/response bodies reference these same component schemas, whose names are validated above.
    schemas.flatMap { case (_, s) => namesIn(s) }.distinct.foreach { case (kind, name) => check(kind, name) }
    // Security-scheme names (both the scheme definitions and the per-operation requirement keys that reference them)
    // are emitted as raw class/trait identifiers (e.g. `case class ${name.capitalize}SecurityIn(...)`).
    doc.components.toSeq.flatMap(_.securitySchemes.keys).distinct.foreach(check("security scheme name", _))
    val requirementKeys = doc.security.flatMap(_.keys) ++
      doc.paths.flatMap(_.methods).flatMap(_.security.toSeq.flatten.flatMap(_.keys))
    requirementKeys.distinct.foreach(check("security requirement", _))
    if (useHeadTagForObjectNames)
      doc.paths.flatMap(_.methods).flatMap(_.tags.toSeq.flatten).distinct.foreach(check("tag", _))
  }
}
