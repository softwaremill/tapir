package sttp.tapir.generic.internal

import magnolia._
import sttp.tapir.SchemaType._
import sttp.tapir.generic.Configuration
import sttp.tapir.{FieldName, Schema, SchemaType, Validator, deprecated, description, encodedName, format, generic}
import SchemaMagnoliaDerivation.deriveCache

import scala.collection.mutable

trait SchemaMagnoliaDerivation {

  type Typeclass[T] = Schema[T]

  def combine[T](ctx: ReadOnlyCaseClass[Schema, T])(implicit genericDerivationConfig: Configuration): Schema[T] = {
    withCache(ctx.typeName, ctx.annotations) {
      val validator = productValidator(ctx)
      val result =
        if (ctx.isValueClass) {
          Schema[T](schemaType = ctx.parameters.head.typeclass.schemaType, validator = validator)
        } else {
          Schema[T](schemaType = productSchemaType(ctx), validator = validator)
        }
      enrichSchema(result, ctx.annotations)
    }
  }

  private def productSchemaType[T](ctx: ReadOnlyCaseClass[Schema, T])(implicit genericDerivationConfig: Configuration): SProduct = SProduct(
    typeNameToObjectInfo(ctx.typeName, ctx.annotations),
    ctx.parameters.map { p =>
      val schema = enrichSchema(p.typeclass, p.annotations)
      val encodedName = getEncodedName(p.annotations).getOrElse(genericDerivationConfig.toEncodedName(p.label))
      (FieldName(p.label, encodedName), schema)
    }.toList
  )

  private def typeNameToObjectInfo(typeName: TypeName, annotations: Seq[Any]): SchemaType.SObjectInfo = {
    def allTypeArguments(tn: TypeName): Seq[TypeName] = tn.typeArguments.flatMap(tn2 => tn2 +: allTypeArguments(tn2))

    annotations.collectFirst { case ann: encodedName => ann.name } match {
      case Some(altName) =>
        SObjectInfo(altName, Nil)
      case None =>
        SObjectInfo(typeName.full, allTypeArguments(typeName).map(_.short).toList)
    }
  }

  private def getEncodedName(annotations: Seq[Any]): Option[String] =
    annotations.collectFirst { case ann: encodedName => ann.name }

  private def enrichSchema[X](schema: Schema[X], annotations: Seq[Any]): Schema[X] = {
    val schemaWithDesc = annotations
      .collectFirst({ case ann: description => ann.text })
      .fold(schema)(schema.description)
    annotations
      .collectFirst({ case ann: format => ann.format })
      .fold(schemaWithDesc)(schemaWithDesc.format)
      .deprecated(isDeprecated(annotations))
  }

  private def isDeprecated(annotations: Seq[Any]): Boolean =
    annotations.collectFirst { case _: deprecated => true } getOrElse false

  def dispatch[T](ctx: SealedTrait[Schema, T])(implicit genericDerivationConfig: Configuration): Schema[T] = {
    withCache(ctx.typeName, ctx.annotations) {
      val baseCoproduct = SCoproduct(typeNameToObjectInfo(ctx.typeName, ctx.annotations), ctx.subtypes.map(_.typeclass).toList, None)
      val coproduct = genericDerivationConfig.discriminator match {
        case Some(d) => baseCoproduct.addDiscriminatorField(FieldName(d))
        case None    => baseCoproduct
      }
      Schema(schemaType = coproduct, validator = coproductValidator(ctx))
    }
  }

  private def productValidator[T](ctx: ReadOnlyCaseClass[Schema, T])(implicit genericDerivationConfig: Configuration): Validator[T] = {
    // type parameters & Nil/List instead of None/Some are needed because of 2.12
    val fieldValidators = ctx.parameters.toList.flatMap { p =>
      val pValidator = p.typeclass.validator
      if (pValidator == Validator.pass) Nil: List[(String, Validator.ProductField[T])]
      else
        List(p.label -> new Validator.ProductField[T] {
          override type FieldType = p.PType
          override def name: FieldName =
            FieldName(p.label, getEncodedName(p.annotations).getOrElse(genericDerivationConfig.toEncodedName(p.label)))
          override def get(t: T): FieldType = p.dereference(t)
          override def validator: Validator[FieldType] = pValidator
        }): List[(String, Validator.ProductField[T])]
    }

    if (fieldValidators.isEmpty) Validator.pass
    else Validator.Product(fieldValidators.toMap)
  }

  private def coproductValidator[T](ctx: SealedTrait[Schema, T])(implicit genericDerivationConfig: Configuration): Validator[T] = {
    Validator.Coproduct(new generic.SealedTrait[Validator, T] {
      override def dispatch(t: T): Validator[T] = ctx.dispatch(t) { v => v.typeclass.validator.asInstanceOf[Validator[T]] }

      override def subtypes: Map[String, Validator[Any]] =
        ctx.subtypes.map(st => st.typeName.full -> st.typeclass.validator.asInstanceOf[Validator[scala.Any]]).toMap
    })
  }

  /** To avoid recursive loops, we keep track of the fully qualified names of types for which derivation is in
    * progress using a mutable Set.
    *
    * We also store all recursive validator references (in a mutable map), which have to be filled in when the top-most
    * recursive validator is generated.
    */
  private def withCache[T](typeName: TypeName, annotations: Seq[Any])(f: => Schema[T]): Schema[T] = {
    val cacheKey = typeName.full
    var cache = deriveCache.get()
    val newCache = cache == null
    if (newCache) {
      cache = (mutable.Set[String](), mutable.Map[String, Validator.Ref[_]]())
      deriveCache.set(cache)
    }

    val (inProgress, validatorRefs) = cache

    if (inProgress.contains(cacheKey)) {
      val validator = Validator.Ref[T]()
      validatorRefs.put(cacheKey, validator)
      Schema[T](SRef(typeNameToObjectInfo(typeName, annotations)), validator = validator)
    } else {
      try {
        inProgress.add(cacheKey)
        val schema = f
        // filling in recursive validators, if there has been a reference
        validatorRefs.get(cacheKey).foreach(ref => ref.asInstanceOf[Validator.Ref[T]].set(schema.validator))
        schema
      } finally {
        inProgress.remove(cacheKey)
        validatorRefs.remove(cacheKey)
        if (newCache) {
          deriveCache.remove()
        }
      }
    }
  }
}

object SchemaMagnoliaDerivation {
  private[internal] val deriveCache: ThreadLocal[(mutable.Set[String], mutable.Map[String, Validator.Ref[_]])] = new ThreadLocal()
}
