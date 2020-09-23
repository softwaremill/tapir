package sttp.tapir.generic.internal

import com.github.ghik.silencer.silent
import magnolia._
import sttp.tapir.SchemaType._
import sttp.tapir.generic.{Configuration, Derived}
import sttp.tapir.{deprecated, description, format, name, FieldName, Schema, SchemaType}
import SchemaMagnoliaDerivation.deriveInProgress

import scala.collection.mutable

trait SchemaMagnoliaDerivation {
  type Typeclass[T] = Schema[T]

  @silent("discarded")
  def combine[T](ctx: ReadOnlyCaseClass[Schema, T])(implicit genericDerivationConfig: Configuration): Schema[T] = {
    withProgressCache { cache =>
      val cacheKey = ctx.typeName.full
      if (cache.contains(cacheKey)) {
        Schema[T](SRef(typeNameToObjectInfo(ctx.typeName, ctx.annotations)))
      } else {
        try {
          cache.add(cacheKey)
          val result =
            if (ctx.isValueClass) {
              Schema[T](ctx.parameters.head.typeclass.schemaType)
            } else {
              Schema[T](
                SProduct(
                  typeNameToObjectInfo(ctx.typeName, ctx.annotations),
                  ctx.parameters.map { p =>
                    val schema = enrichSchema(p.typeclass, p.annotations)
                    val altName = getAltName(p.annotations).getOrElse(p.label)
                    (FieldName(p.label, genericDerivationConfig.toLowLevelName(altName)), schema)
                  } toList
                )
              )
            }
          enrichSchema(result, ctx.annotations)
        } finally {
          cache.remove(cacheKey)
        }
      }
    }
  }

  private def typeNameToObjectInfo(typeName: TypeName, annotations: Seq[Any]): SchemaType.SObjectInfo = {
    def allTypeArguments(tn: TypeName): Seq[TypeName] = tn.typeArguments.flatMap(tn2 => tn2 +: allTypeArguments(tn2))

    annotations.collectFirst { case ann: name => ann.name } match {
      case Some(altName) =>
        SObjectInfo(altName, Nil)
      case None =>
        SObjectInfo(typeName.full, allTypeArguments(typeName).map(_.short).toList)
    }
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

  private def getAltName(annotations: Seq[Any]): Option[String] =
    annotations.collectFirst { case ann: name => ann.name }

  private def enrichSchema[X](schema: Schema[X], annotations: Seq[Any]): Schema[X] = {
    val schemaWithDesc = annotations.collectFirst({ case ann: description => ann.text })
      .fold(schema)(schema.description)
    annotations.collectFirst({ case ann: format => ann.format })
      .fold(schemaWithDesc)(schemaWithDesc.format)
      .deprecated(isDeprecated(annotations))
  }

  private def isDeprecated(annotations: Seq[Any]): Boolean =
    annotations.collectFirst { case _: deprecated => true } getOrElse false

  def dispatch[T](ctx: SealedTrait[Schema, T]): Schema[T] = {
    Schema(SCoproduct(typeNameToObjectInfo(ctx.typeName, ctx.annotations), ctx.subtypes.map(_.typeclass).toList, None))
  }

  implicit def schemaForCaseClass[T]: Derived[Schema[T]] = macro MagnoliaDerivedMacro.derivedGen[T]
}

object SchemaMagnoliaDerivation {
  private[internal] val deriveInProgress: ThreadLocal[mutable.Set[String]] = new ThreadLocal()
}
