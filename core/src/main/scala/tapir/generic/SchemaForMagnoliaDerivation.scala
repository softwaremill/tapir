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
    withProgressCache { cache =>
      val cacheKey = ctx.typeName.full
      if (cache.contains(cacheKey)) {
        new SchemaFor[T] {
          override val schema: Schema = SRef(ctx.typeName.full)
        }
      } else {
        try {
          cache.add(cacheKey)
          new SchemaFor[T] {
            override val schema: Schema = SObject(
              SObjectInfo(ctx.typeName.short, ctx.typeName.full),
              ctx.parameters.map(p => (genericDerivationConfig.transformMemberName(p.label), p.typeclass.schema)).toList,
              ctx.parameters.filter(!_.typeclass.isOptional).map(p => genericDerivationConfig.transformMemberName(p.label))
            )
          }
        } finally {
          cache.remove(cacheKey)
        }
      }
    }
  }

  private def withProgressCache[T](f: mutable.Set[String] => SchemaFor[T]): SchemaFor[T] = {
    var cache = deriveInProgress.get()
    val newCache = cache == null
    if (newCache) {
      cache = mutable.Set[String]()
      deriveInProgress.set(cache)
    }

    try f(cache)
    finally {
      if (newCache) {
        deriveInProgress.remove()
      }
    }
  }

  def dispatch[T](ctx: SealedTrait[SchemaFor, T]): SchemaFor[T] = {
    throw new RuntimeException("Sealed trait hierarchies are not yet supported")
  }

  implicit def schemaForCaseClass[T]: SchemaFor[T] = macro Magnolia.gen[T]
}

object SchemaForMagnoliaDerivation {
  private[generic] val deriveInProgress: ThreadLocal[mutable.Set[String]] = ThreadLocal.withInitial(() => mutable.Set[String]())
}
