package sttp.tapir.generic.auto

import magnolia1._
import sttp.tapir.SchemaType._
import sttp.tapir.generic.Configuration
import sttp.tapir.generic.auto.SchemaMagnoliaDerivation.deriveCache
import sttp.tapir.internal.IterableToListMap
import sttp.tapir.{FieldName, Schema, SchemaType}

import scala.collection.mutable

trait SchemaMagnoliaDerivation {

  type Typeclass[T] = Schema[T]

  def join[T](ctx: ReadOnlyCaseClass[Schema, T])(implicit genericDerivationConfig: Configuration): Schema[T] = {
    withCache(ctx.typeName, ctx.annotations) {
      val result =
        if (ctx.isValueClass) {
          require(ctx.parameters.nonEmpty, s"Cannot derive schema for generic value class: ${ctx.typeName.owner}")
          val valueSchema = ctx.parameters.head.typeclass
          Schema[T](schemaType = valueSchema.schemaType.asInstanceOf[SchemaType[T]], format = valueSchema.format)
        } else {
          // Not using inherited annotations when generating type name, we don't want @encodedName to be inherited for types
          Schema[T](schemaType = productSchemaType(ctx), name = Some(typeNameToSchemaName(ctx.typeName, ctx.annotations)))
        }
      enrichSchema(result, mergeAnnotations(ctx.annotations, ctx.inheritedAnnotations))
    }
  }

  private def productSchemaType[T](ctx: ReadOnlyCaseClass[Schema, T])(implicit genericDerivationConfig: Configuration): SProduct[T] =
    SProduct(
      ctx.parameters.map { p =>
        val annotations = mergeAnnotations(p.annotations, p.inheritedAnnotations)
        val pSchema = enrichSchema(p.typeclass, annotations)
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

  private def subtypeNameToSchemaName(subtype: Subtype[Typeclass, _]): Schema.SName =
    typeNameToSchemaName(subtype.typeName, subtype.annotations)

  private def getEncodedName(annotations: Seq[Any]): Option[String] =
    annotations.collectFirst { case ann: Schema.annotations.encodedName => ann.name }

  private def enrichSchema[X](schema: Schema[X], annotations: Seq[Any]): Schema[X] = {
    annotations.foldLeft(schema) {
      case (schema, ann: Schema.annotations.description)    => schema.description(ann.text)
      case (schema, ann: Schema.annotations.encodedExample) => schema.encodedExample(ann.example)
      case (schema, ann: Schema.annotations.default[X])     => schema.default(ann.default, ann.encoded)
      case (schema, ann: Schema.annotations.validate[X])    => schema.validate(ann.v)
      case (schema, ann: Schema.annotations.validateEach[X]) =>
        schema.modifyUnsafe(Schema.ModifyCollectionElements)((_: Schema[X]).validate(ann.v))
      case (schema, ann: Schema.annotations.format)    => schema.format(ann.format)
      case (schema, ann: Schema.annotations.title)     => schema.title(ann.name)
      case (schema, _: Schema.annotations.deprecated)  => schema.deprecated(true)
      case (schema, ann: Schema.annotations.customise) => ann.f(schema).asInstanceOf[Schema[X]]
      case (schema, _)                                 => schema
    }
  }

  def split[T](ctx: SealedTrait[Schema, T])(implicit genericDerivationConfig: Configuration): Schema[T] = {
    withCache(ctx.typeName, ctx.annotations) {
      val subtypesByName =
        ctx.subtypes
          .map(s => subtypeNameToSchemaName(s) -> s.typeclass.asInstanceOf[Typeclass[T]])
          .toListMap
      val baseCoproduct =
        SCoproduct(subtypesByName.values.toList, None)((t: T) =>
          ctx.split(t) { v =>
            for {
              schema <- subtypesByName.get(subtypeNameToSchemaName(v))
              value <- v.cast.lift(t)
            } yield SchemaWithValue(schema, value)
          }
        )
      val coproduct = genericDerivationConfig.discriminator match {
        case Some(d) =>
          val discriminatorMapping: Map[String, SRef[_]] =
            ctx.subtypes.map { s =>
              val schemaName = subtypeNameToSchemaName(s)
              genericDerivationConfig.toDiscriminatorValue(schemaName) -> SRef(schemaName)
            }.toMap
          baseCoproduct.addDiscriminatorField(
            FieldName(d),
            discriminatorMapping = discriminatorMapping
          )
        case None => baseCoproduct
      }
      Schema(schemaType = coproduct, name = Some(typeNameToSchemaName(ctx.typeName, ctx.annotations)))
    }
  }

  private def mergeAnnotations[T](primary: Seq[Any], inherited: Seq[Any]): Seq[Any] =
    primary ++ inherited.distinct.filter {
      // skip inherited annotation from definition if defined in implementation
      case a if primary.exists(_.getClass.equals(a.getClass)) => false
      case _                                                  => true
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
  private[auto] val deriveCache: ThreadLocal[mutable.Set[String]] = new ThreadLocal()
}
