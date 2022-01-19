package sttp.tapir.internal

import sttp.tapir.SchemaType._
import sttp.tapir.generic.Configuration
import sttp.tapir.{FieldName, Schema, SchemaType}
import SchemaMagnoliaDerivation.deriveCache
import sttp.tapir.internal.IterableToListMap

import scala.collection.mutable
import magnolia1._
import scala.deriving.Mirror

trait SchemaMagnoliaDerivation {

  inline def derived[T](using genericDerivationConfig: Configuration, m: Mirror.Of[T]): Schema[T] = {
    val derivation = new Derivation[Schema] {
      type Typeclass[T] = Schema[T]

      override def join[T](ctx: CaseClass[Schema, T]): Schema[T] = {
        withCache(ctx.typeInfo, ctx.annotations) {
          val result =
            if (ctx.isValueClass) {
              val valueSchema = ctx.params.head.typeclass
              Schema[T](schemaType = valueSchema.schemaType.asInstanceOf[SchemaType[T]], format = valueSchema.format)
            } else {
              Schema[T](schemaType = productSchemaType(ctx), name = Some(typeNameToSchemaName(ctx.typeInfo, ctx.annotations)))
            }
          enrichSchema(result, ctx.annotations)
        }
      }

      private def productSchemaType[T](ctx: CaseClass[Schema, T]): SProduct[T] =
        SProduct(
          ctx.params.map { p =>
            val pSchema = enrichSchema(p.typeclass, p.annotations)
            val encodedName = getEncodedName(p.annotations).getOrElse(genericDerivationConfig.toEncodedName(p.label))

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

      private def getEncodedName(annotations: Seq[Any]): Option[String] =
        annotations.collectFirst { case ann: Schema.annotations.encodedName => ann.name }

      private def enrichSchema[X](schema: Schema[X], annotations: Seq[Any]): Schema[X] = {
        annotations.foldLeft(schema) {
          case (schema, ann: Schema.annotations.description)            => schema.description(ann.text)
          case (schema, ann: Schema.annotations.encodedExample)         => schema.encodedExample(ann.example)
          case (schema, ann: Schema.annotations.default[X @unchecked])  => schema.default(ann.default)
          case (schema, ann: Schema.annotations.validate[X @unchecked]) => schema.validate(ann.v)
          case (schema, ann: Schema.annotations.format)                 => schema.format(ann.format)
          case (schema, _: Schema.annotations.deprecated)               => schema.deprecated(true)
          case (schema, _)                                              => schema
        }
      }

      override def split[T](ctx: SealedTrait[Schema, T]): Schema[T] = {
        withCache(ctx.typeInfo, ctx.annotations) {
          val subtypesByName =
            ctx.subtypes.toList
              .map(s => typeNameToSchemaName(s.typeInfo, s.annotations) -> s.typeclass.asInstanceOf[Typeclass[T]])
              .toListMap
          val baseCoproduct = SCoproduct(subtypesByName.values.toList, None)((t: T) =>
            ctx.choose(t) { v => subtypesByName.get(typeNameToSchemaName(v.typeInfo, v.annotations)) }
          )
          val coproduct = genericDerivationConfig.discriminator match {
            case Some(d) => baseCoproduct.addDiscriminatorField(FieldName(d))
            case None    => baseCoproduct
          }

          Schema(schemaType = coproduct, name = Some(typeNameToSchemaName(ctx.typeInfo, ctx.annotations)))
        }
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
  private[internal] val deriveCache: ThreadLocal[mutable.Set[String]] = new ThreadLocal()
}
