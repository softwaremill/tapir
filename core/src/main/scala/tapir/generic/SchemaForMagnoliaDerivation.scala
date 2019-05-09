package tapir.generic

import magnolia._
import tapir.Schema._
import tapir.generic.SchemaForMagnoliaDerivation.deriveInProgress
import tapir.{Schema, SchemaFor}

import scala.collection.mutable
import scala.language.experimental.macros

trait SchemaForMagnoliaDerivation {
  type Typeclass[T] = SchemaFor[T]

  def combine[T](ctx: CaseClass[SchemaFor, T])(implicit genericDerivationConfig: Configuration): SchemaFor[T] = {
    withProgressCache { cache =>
      val cacheKey = ctx.typeName.full
      if (cache.contains(cacheKey)) {
        new SchemaFor[T] {
          override val schema: Schema = SRef(typeNameToObjectInfo(ctx.typeName))
        }
      } else {
        try {
          cache.add(cacheKey)
          if (ctx.isValueClass) {
            new SchemaFor[T] {
              override val schema: Schema = ctx.parameters.head.typeclass.schema
            }
          } else {
            new SchemaFor[T] {
              override val schema: Schema = SProduct(
                typeNameToObjectInfo(ctx.typeName),
                ctx.parameters.map(p => (genericDerivationConfig.transformMemberName(p.label), p.typeclass.schema)).toList,
                ctx.parameters.filter(!_.typeclass.isOptional).map(p => genericDerivationConfig.transformMemberName(p.label))
              )
            }
          }
        } finally {
          cache.remove(cacheKey)
        }
      }
    }
  }

  private def typeNameToObjectInfo(typeName: TypeName): Schema.SObjectInfo = {
    def allTypeArguments(tn: TypeName): Seq[TypeName] = tn.typeArguments.flatMap(tn2 => tn2 +: allTypeArguments(tn2))
    SObjectInfo(typeName.full, allTypeArguments(typeName).map(_.short).toList)
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
    SchemaFor(SCoproduct(typeNameToObjectInfo(ctx.typeName), ctx.subtypes.map(_.typeclass.schema).toSet, None))
  }

  implicit def schemaForCaseClass[T]: SchemaFor[T] = macro Magnolia.gen[T]
}

object SchemaForMagnoliaDerivation {
  private[generic] val deriveInProgress: ThreadLocal[mutable.Set[String]] = ThreadLocal.withInitial(() => mutable.Set[String]())
}
