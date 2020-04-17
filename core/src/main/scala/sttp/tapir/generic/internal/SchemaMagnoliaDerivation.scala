package sttp.tapir.generic.internal

import com.github.ghik.silencer.silent
import magnolia._
import sttp.tapir.SchemaType._
import sttp.tapir.generic.{Configuration, Derived}
import sttp.tapir.{Schema, SchemaType}
import SchemaMagnoliaDerivation.deriveInProgress

import scala.collection.mutable
import scala.language.experimental.macros

trait SchemaMagnoliaDerivation {
  type Typeclass[T] = Schema[T]

  @silent("discarded")
  def combine[T](ctx: ReadOnlyCaseClass[Schema, T])(implicit genericDerivationConfig: Configuration): Schema[T] = {
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
                ctx.parameters.map(p => (genericDerivationConfig.toLowLevelName(p.label), p.typeclass)).toList
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

  implicit def schemaForCaseClass[T]: Derived[Schema[T]] = macro MagnoliaDerivedMacro.derivedGen[T]
}

object SchemaMagnoliaDerivation {
  private[internal] val deriveInProgress: ThreadLocal[mutable.Set[String]] = new ThreadLocal()
}
