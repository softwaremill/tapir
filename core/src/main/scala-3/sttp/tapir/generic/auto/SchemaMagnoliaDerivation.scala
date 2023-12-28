package sttp.tapir.generic.auto

import magnolia1.*
import sttp.tapir.SchemaType.*
import sttp.tapir.generic.Configuration
import sttp.tapir.generic.auto.SchemaMagnoliaDerivation.deriveCache
import sttp.tapir.internal.IterableToListMap
import sttp.tapir.{FieldName, Schema, SchemaType}

import scala.collection.mutable
import scala.deriving.Mirror

trait SchemaMagnoliaDerivation {

  inline def derived[T](using genericDerivationConfig: Configuration, m: Mirror.Of[T]): Schema[T] = {
    val derivation = new Derivation[Schema] {
      type Typeclass[T] = Schema[T]

      override def join[T](ctx: CaseClass[Schema, T]): Schema[T] = {
        withCache(ctx.typeInfo, ctx.annotations) {
          val result =
            if (ctx.isValueClass) {
              require(ctx.params.nonEmpty, s"Cannot derive schema for generic value class: ${ctx.typeInfo.owner}")
              val valueSchema = ctx.params.head.typeclass
              Schema[T](schemaType = valueSchema.schemaType.asInstanceOf[SchemaType[T]], format = valueSchema.format)
            } else {
              // Not using inherited annotations when generating type name, we don't want @encodedName to be inherited for types
              Schema[T](schemaType = productSchemaType(ctx), name = Some(typeNameToSchemaName(ctx.typeInfo, ctx.annotations)))
            }
          enrichSchema(result, mergeAnnotations(ctx.annotations, ctx.inheritedAnnotations))
        }
      }

      private def productSchemaType[T](ctx: CaseClass[Schema, T]): SProduct[T] =
        SProduct(
          ctx.params.map { p =>
            val annotations = mergeAnnotations(p.annotations, p.inheritedAnnotations)
            val pSchema = enrichSchema(p.typeclass, annotations)
            val encodedName = getEncodedName(annotations).getOrElse(genericDerivationConfig.toEncodedName(p.label))

            SProductField[T, p.PType](FieldName(p.label, encodedName), pSchema, t => Some(p.deref(t)))
          }.toList
        )

      private def typeNameToSchemaName(typeName: TypeInfo, annotations: Seq[Any]): Schema.SName = {
        def allTypeArguments(tn: TypeInfo): Seq[TypeInfo] = tn.typeParams.toList.flatMap(tn2 => tn2 +: allTypeArguments(tn2))

        annotations.collectFirst { case ann: Schema.annotations.encodedName => ann.name } match {
          case Some(altName) =>
            Schema.SName(altName, Nil)
          case None =>
            Schema.SName(typeName.full, allTypeArguments(typeName).map(_.short).toList)
        }
      }

      private def subtypeNameToSchemaName(subtype: SealedTrait.Subtype[Typeclass, _, ?]): Schema.SName =
        typeNameToSchemaName(subtype.typeInfo, subtype.annotations)

      private def getEncodedName(annotations: Seq[Any]): Option[String] =
        annotations.collectFirst { case ann: Schema.annotations.encodedName => ann.name }

      private def enrichSchema[X](schema: Schema[X], annotations: Seq[Any]): Schema[X] = {
        annotations.foldLeft(schema) {
          case (schema, ann: Schema.annotations.description)            => schema.description(ann.text)
          case (schema, ann: Schema.annotations.encodedExample)         => schema.encodedExample(ann.example)
          case (schema, ann: Schema.annotations.default[X @unchecked])  => schema.default(ann.default, ann.encoded)
          case (schema, ann: Schema.annotations.validate[X @unchecked]) => schema.validate(ann.v)
          case (schema, ann: Schema.annotations.validateEach[X @unchecked]) =>
            schema.modifyUnsafe(Schema.ModifyCollectionElements)((_: Schema[X]).validate(ann.v))
          case (schema, ann: Schema.annotations.format)    => schema.format(ann.format)
          case (schema, ann: Schema.annotations.title)     => schema.title(ann.name)
          case (schema, _: Schema.annotations.deprecated)  => schema.deprecated(true)
          case (schema, ann: Schema.annotations.customise) => ann.f(schema).asInstanceOf[Schema[X]]
          case (schema, _)                                 => schema
        }
      }

      override def split[T](ctx: SealedTrait[Schema, T]): Schema[T] = {
        val annotations = mergeAnnotations(ctx.annotations, ctx.inheritedAnnotations)
        withCache(ctx.typeInfo, ctx.annotations) {
          val subtypesByName =
            ctx.subtypes.toList
              .map(s =>
                typeNameToSchemaName(s.typeInfo, s.annotations) -> s.typeclass
                  .asInstanceOf[Typeclass[T]]
              )
              .toListMap
          val baseCoproduct = SCoproduct(subtypesByName.values.toList, None)((t: T) =>
            ctx.choose(t) { v =>
              subtypesByName.get(subtypeNameToSchemaName(v.subtype)).map(s => SchemaWithValue(s, v.value))
            }
          )
          val coproduct = genericDerivationConfig.discriminator match {
            case Some(d) =>
              val discriminatorMapping: Map[String, SRef[_]] =
                ctx.subtypes.map { s =>
                  val schemaName = subtypeNameToSchemaName(s)
                  genericDerivationConfig.toDiscriminatorValue(schemaName) -> SRef(schemaName)
                }.toMap
              baseCoproduct.addDiscriminatorField(FieldName(d), discriminatorMapping = discriminatorMapping)
            case None => baseCoproduct
          }

          Schema(schemaType = coproduct, name = Some(typeNameToSchemaName(ctx.typeInfo, ctx.annotations)))
        }
      }

      private def mergeAnnotations[T](primary: Seq[Any], inherited: Seq[Any]): Seq[Any] =
        primary ++ inherited.distinct.filter {
          // skip inherited annotation from definition if defined in implementation
          case a if primary.exists(_.getClass.equals(a.getClass)) => false
          case _                                                  => true
        }

      /** To avoid recursive loops, we keep track of the fully qualified names of types for which derivation is in progress using a mutable
        * Set.
        */
      private def withCache[T](typeName: TypeInfo, annotations: Seq[Any])(f: => Schema[T]): Schema[T] = {
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

    derivation.derived[T]
  }
}

object SchemaMagnoliaDerivation {
  private[auto] val deriveCache: ThreadLocal[mutable.Set[String]] = new ThreadLocal()
}
