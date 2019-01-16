package tapir.generic

import magnolia._
import tapir.{Schema, SchemaFor}
import tapir.Schema._

import scala.language.experimental.macros

trait SchemaForMagnoliaDerivation {
  type Typeclass[T] = SchemaFor[T]

  def combine[T](ctx: CaseClass[SchemaFor, T])(implicit genericDerivationConfig: Configuration): SchemaFor[T] = {
    new SchemaFor[T] {
      override val schema: Schema = SObject(
        SObjectInfo(ctx.typeName.short, ctx.typeName.full),
        ctx.parameters.map(p => (genericDerivationConfig.transformMemberName(p.label), p.typeclass.schema)).toList,
        ctx.parameters.filter(!_.typeclass.isOptional).map(p => genericDerivationConfig.transformMemberName(p.label))
      )
    }
  }

  def dispatch[T](ctx: SealedTrait[SchemaFor, T]): SchemaFor[T] = {
    throw new RuntimeException("Sealed trait hierarchies are not yet supported")
  }

  implicit def schemaForCaseClass[T]: SchemaFor[T] = macro Magnolia.gen[T]
}
