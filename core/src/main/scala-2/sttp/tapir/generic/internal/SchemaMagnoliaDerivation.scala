package sttp.tapir.generic.internal

import magnolia1._
import sttp.tapir.SchemaType._
import sttp.tapir.generic.Configuration
import sttp.tapir.{FieldName, Schema, SchemaType}
import SchemaMagnoliaDerivation.deriveCache
import sttp.tapir.internal.IterableToListMap

import scala.collection.mutable

trait SchemaMagnoliaDerivation {

  type Typeclass[T] = Schema[T]

  def join[T](ctx: ReadOnlyCaseClass[Schema, T])(implicit genericDerivationConfig: Configuration): Schema[T] = {
    withCache(ctx.typeName, ctx.annotations) {
      val result =
        if (ctx.isValueClass) {
          Schema[T](schemaType = ctx.parameters.head.typeclass.schemaType.asInstanceOf[SchemaType[T]])
        } else {
          Schema[T](schemaType = productSchemaType(ctx), name = Some(typeNameToSchemaName(ctx.typeName, ctx.annotations)))
        }
      enrichSchema(result, ctx.annotations)
    }
  }

  private def productSchemaType[T](ctx: ReadOnlyCaseClass[Schema, T])(implicit genericDerivationConfig: Configuration): SProduct[T] =
    SProduct(
      ctx.parameters.map { p =>
        val pSchema = enrichSchema(p.typeclass, p.annotations)
        val encodedName = getEncodedName(p.annotations).getOrElse(genericDerivationConfig.toEncodedName(p.label))

        SProductField[T, p.PType](FieldName(p.label, encodedName), pSchema, t => Some(p.dereference(t)))
      }.toList
    )

  private def typeNameToSchemaName(typeName: TypeName, annotations: Seq[Any]): Schema.SName = {
    def allTypeArguments(tn: TypeName): Seq[TypeName] = tn.typeArguments.flatMap(tn2 => tn2 +: allTypeArguments(tn2))

    annotations.collectFirst { case ann: Schema.annotations.encodedName => ann.name } match {
      case Some(altName) =>
        Schema.SName(altName, Nil)
      case None =>
        Schema.SName(typeName.full, allTypeArguments(typeName).map(_.short).toList)
    }
  }

  private def getEncodedName(annotations: Seq[Any]): Option[String] =
    annotations.collectFirst { case ann: Schema.annotations.encodedName => ann.name }

  private def enrichSchema[X](schema: Schema[X], annotations: Seq[Any]): Schema[X] = {
    annotations.foldLeft(schema) {
      case (schema, ann: Schema.annotations.description)    => schema.description(ann.text)
      case (schema, ann: Schema.annotations.encodedExample) => schema.encodedExample(ann.example)
      case (schema, ann: Schema.annotations.default[X])     => schema.default(ann.default)
      case (schema, ann: Schema.annotations.validate[X])    => schema.validate(ann.v)
      case (schema, ann: Schema.annotations.format)         => schema.format(ann.format)
      case (schema, _: Schema.annotations.deprecated)       => schema.deprecated(true)
      case (schema, _)                                      => schema
    }
  }

  def split[T](ctx: SealedTrait[Schema, T])(implicit genericDerivationConfig: Configuration): Schema[T] = {
    withCache(ctx.typeName, ctx.annotations) {
      val subtypesByName =
        ctx.subtypes.map(s => typeNameToSchemaName(s.typeName, s.annotations) -> s.typeclass.asInstanceOf[Typeclass[T]]).toListMap
      val baseCoproduct = SCoproduct(subtypesByName.values.toList, None)((t: T) =>
        ctx.split(t) { v => subtypesByName.get(typeNameToSchemaName(v.typeName, v.annotations)) }
      )
      val coproduct = genericDerivationConfig.discriminator match {
        case Some(d) => baseCoproduct.addDiscriminatorField(FieldName(d))
        case None    => baseCoproduct
      }
      Schema(schemaType = coproduct, name = Some(typeNameToSchemaName(ctx.typeName, ctx.annotations)))
    }
  }

  /** To avoid recursive loops, we keep track of the fully qualified names of types for which derivation is in progress using a mutable Set.
    */
  private def withCache[T](typeName: TypeName, annotations: Seq[Any])(f: => Schema[T]): Schema[T] = {
    val cacheKey = typeName.full
    var inProgress = deriveCache.get()
    val newCache = inProgress == null
    if (newCache) {
      inProgress = mutable.Set[String]()
      deriveCache.set(inProgress)
    }

    if (inProgress.contains(cacheKey)) {
      Schema[T](SRef(typeNameToSchemaName(typeName, annotations)))
    } else {
      try {
        inProgress.add(cacheKey)
        val schema = f
        schema
      } finally {
        inProgress.remove(cacheKey)
        if (newCache) {
          deriveCache.remove()
        }
      }
    }
  }
}

object SchemaMagnoliaDerivation {
  private[internal] val deriveCache: ThreadLocal[mutable.Set[String]] = new ThreadLocal()
}
