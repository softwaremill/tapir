package tapir.generic

import magnolia._
import tapir.{Schema, SchemaFor}
import tapir.Schema._

import scala.collection.mutable
import scala.language.experimental.macros

trait SchemaForMagnoliaDerivation {
  type Typeclass[T] = SchemaFor[T]

  private val deriveInProgress = mutable.Set[String]()

  def combine[T](ctx: CaseClass[SchemaFor, T]): SchemaFor[T] = {
    if (deriveInProgress.contains(ctx.typeName.full)) {
      new SchemaFor[T] {
        override def schema: Schema = SRef(ctx.typeName.full)
      }
    } else {
      withProgressCache(ctx) {
        new SchemaFor[T] {
          override val schema: Schema = SObject(
            SObjectInfo(ctx.typeName.short, ctx.typeName.full),
            ctx.parameters.map(p => (p.label, p.typeclass.schema)).toList,
            ctx.parameters.filter(!_.typeclass.isOptional).map(_.label)
          )
        }
      }
    }
  }

  private def withProgressCache[T](ctx: CaseClass[SchemaFor, T])(f: => SchemaFor[T]): SchemaFor[T] = {
    val fullName = ctx.typeName.full
    deriveInProgress.add(fullName)
    val result = f
    deriveInProgress.remove(fullName)
    result
  }

  def dispatch[T](ctx: SealedTrait[SchemaFor, T]): SchemaFor[T] = {
    throw new RuntimeException("Sealed trait hierarchies are not yet supported")
  }

  implicit def schemaForCaseClass[T]: SchemaFor[T] = macro Magnolia.gen[T]
}
