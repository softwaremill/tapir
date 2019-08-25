package tapir.docs.openapi.schema

import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi.{Schema => OSchema, _}
import tapir.{BaseCollectionValidator, CollectionValidator, Constraint, ProductValidator, Validator, ValueValidator, Schema => TSchema}

/**
  * Converts a tapir schema to an OpenAPI schema, using the given map to resolve references.
  */
private[schema] class TSchemaToOSchema(schemaReferenceMapper: SchemaReferenceMapper, discriminatorToOpenApi: DiscriminatorToOpenApi) {
  def apply(schemaWithValidator: (TSchema, Validator[_])): ReferenceOr[OSchema] = {
    schemaWithValidator match {
      case (TSchema.SInteger, v) =>
        Right(OSchema(SchemaType.Integer).copy(minimum = minimum(constraints(v))))
      case (TSchema.SNumber, _) =>
        Right(OSchema(SchemaType.Number))
      case (TSchema.SBoolean, _) =>
        Right(OSchema(SchemaType.Boolean))
      case (TSchema.SString, v: Validator[String]) =>
        Right(OSchema(SchemaType.String).copy(pattern = pattern(constraints(v))))
      case (TSchema.SProduct(_, fields, required), v: Validator[_]) =>
        Right(
          OSchema(SchemaType.Object).copy(
            required = required.toList,
            properties = fields.map {
              case (fieldName, s: TSchema.SObject) =>
                fieldName -> Left(schemaReferenceMapper.map(s.info))
              case (fieldName, fieldSchema) =>
                fieldName -> apply(fieldSchema -> (v match {
                  case ProductValidator(vFields) => vFields(fieldName).validator
                  case _                         => Validator.passing
                }))
            }.toListMap
          )
        )
      case (TSchema.SArray(el: TSchema.SObject), v: BaseCollectionValidator[_, _]) =>
        Right(
          OSchema(SchemaType.Array).copy(
            items = Some(Left(schemaReferenceMapper.map(el.info))),
            minSize = minSize(constraints(v))
          )
        )
      case (TSchema.SArray(el), v: BaseCollectionValidator[_, _]) =>
        Right(
          OSchema(SchemaType.Array).copy(
            items = Some(apply(el -> v.elementValidator)),
            minSize = minSize(constraints(v))
          )
        )
      case (TSchema.SBinary, _) =>
        Right(OSchema(SchemaType.String).copy(format = Some(SchemaFormat.Binary)))
      case (TSchema.SDate, _) =>
        Right(OSchema(SchemaType.String).copy(format = Some(SchemaFormat.Date)))
      case (TSchema.SDateTime, _) =>
        Right(OSchema(SchemaType.String).copy(format = Some(SchemaFormat.DateTime)))
      case (TSchema.SRef(fullName), _) =>
        Left(schemaReferenceMapper.map(fullName))
      case (TSchema.SCoproduct(_, schemas, d), _) =>
        Right(
          OSchema.apply(
            schemas.collect { case s: TSchema.SProduct => Left(schemaReferenceMapper.map(s.info)) }.toList,
            d.map(discriminatorToOpenApi.apply)
          )
        )
      case (TSchema.SOpenProduct(_, valueSchema), v) =>
        Right(
          OSchema(SchemaType.Object).copy(
            required = List.empty,
            additionalProperties = Some(valueSchema match {
              case so: TSchema.SObject => Left(schemaReferenceMapper.map(so.info))
              case s                   => apply(s -> Validator.passing)
            })
          )
        )
    }
  }

  private def constraints[T](v: Validator[T]): List[Constraint[_]] = {
    v.unwrap match {
      case ValueValidator(c)             => c
      case BaseCollectionValidator(_, c) => c
      case _                             => List.empty
    }
  }
  private def minimum(constraints: List[Constraint[_]]): Option[Int] = {
    constraints.collectFirst { case Constraint.Minimum(v: Int) => v }
  }
  private def pattern(constraints: List[Constraint[_]]): Option[String] = {
    constraints.collectFirst { case Constraint.Pattern(v: String) => v }
  }
  private def minSize(constraints: List[Constraint[_]]): Option[Int] = {
    constraints.collectFirst { case Constraint.MinSize(v) => v }
  }
}
