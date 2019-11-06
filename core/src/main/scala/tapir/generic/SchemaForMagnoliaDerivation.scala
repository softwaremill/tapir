package tapir.generic

import com.github.ghik.silencer.silent
import magnolia._
import tapir.SchemaType._
import tapir.generic.SchemaForMagnoliaDerivation.deriveInProgress
import tapir.{SchemaType, Schema}

import scala.collection.mutable
import scala.language.experimental.macros

trait SchemaForMagnoliaDerivation {
  type Typeclass[T] = Schema[T]

  @silent("discarded")
  def combine[T](ctx: CaseClass[Schema, T])(implicit genericDerivationConfig: Configuration): Schema[T] = {
    withProgressCache { cache =>
      val cacheKey = ctx.typeName.full
      if (cache.contains(cacheKey)) {
        Schema[T](SRef(typeNameToObjectInfo(ctx.typeName)))
      } else {
        try {
          cache.add(cacheKey)
          if (ctx.isValueClass) {
            Schema[T](ctx.parameters.head.typeclass.schemaType)
          } else {
            Schema[T](
              SProduct(
                typeNameToObjectInfo(ctx.typeName),
                ctx.parameters.map(p => (genericDerivationConfig.transformMemberName(p.label), p.typeclass)).toList
              )
            )
          }
        } finally {
          cache.remove(cacheKey)
        }
      }
    }
  }

  private def typeNameToObjectInfo(typeName: TypeName): SchemaType.SObjectInfo = {
    def allTypeArguments(tn: TypeName): Seq[TypeName] = tn.typeArguments.flatMap(tn2 => tn2 +: allTypeArguments(tn2))
    SObjectInfo(typeName.full, allTypeArguments(typeName).map(_.short).toList)
  }

  private def withProgressCache[T](f: mutable.Set[String] => Schema[T]): Schema[T] = {
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

  def dispatch[T](ctx: SealedTrait[Schema, T]): Schema[T] = {
    Schema(SCoproduct(typeNameToObjectInfo(ctx.typeName), ctx.subtypes.map(_.typeclass).toList, None))
  }

  implicit def schemaForCaseClass[T]: Schema[T] = macro Magnolia.gen[T]
}

object SchemaForMagnoliaDerivation {
  private[generic] val deriveInProgress: ThreadLocal[mutable.Set[String]] = new ThreadLocal()
}
