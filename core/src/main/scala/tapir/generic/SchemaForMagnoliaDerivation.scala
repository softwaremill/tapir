package tapir.generic

import magnolia._
import tapir.{Schema, SchemaFor}
import tapir.Schema._

import scala.collection.mutable
import scala.language.experimental.macros

import SchemaForMagnoliaDerivation.deriveInProgress

trait SchemaForMagnoliaDerivation {
  type Typeclass[T] = SchemaFor[T]

  def combine[T](ctx: CaseClass[SchemaFor, T])(implicit genericDerivationConfig: Configuration): SchemaFor[T] = {
    if (deriveInProgress.contains(ctx.typeName.full)) {
      new SchemaFor[T] {
        override val schema: Schema = SRef(ctx.typeName.full)
      }
    } else {
      withProgressCache(ctx) {
        new SchemaFor[T] {
          override val schema: Schema = SObject(
            SObjectInfo(ctx.typeName.short, ctx.typeName.full),
            ctx.parameters.map(p => (genericDerivationConfig.transformMemberName(p.label), p.typeclass.schema)).toList,
            ctx.parameters.filter(!_.typeclass.isOptional).map(p => genericDerivationConfig.transformMemberName(p.label))
          )
        }
      }
    }
  }

  private def withProgressCache[T](ctx: CaseClass[SchemaFor, T])(f: => SchemaFor[T]): SchemaFor[T] = {
    val fullName = ctx.typeName.full
    try {
      println(s"ADD $fullName ${Thread.currentThread().getId}")
      deriveInProgress.add(fullName)
      f
    } finally {
      println(s"REM $fullName ${Thread.currentThread().getId}") // TODO
      deriveInProgress.remove(fullName)
    }
  }

  def dispatch[T](ctx: SealedTrait[SchemaFor, T]): SchemaFor[T] = {
    throw new RuntimeException("Sealed trait hierarchies are not yet supported")
  }

  implicit def schemaForCaseClass[T]: SchemaFor[T] = macro Magnolia.gen[T]
}

object SchemaForMagnoliaDerivation {
  private[generic] val deriveInProgress = mutable.Set[String]()
}
