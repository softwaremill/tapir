package sttp.tapir

import sttp.tapir.generic.SealedTrait

/** The type of the low-level representation of a `T` values. Part of [[Schema]]s. */
sealed trait SchemaType[T] {
  def show: String
  def contramap[TT](g: TT => T): SchemaType[TT]
}

object SchemaType {
  case class SString[T]() extends SchemaType[T] {
    def show: String = "string"
    override def contramap[TT](g: TT => T): SchemaType[TT] = SString()
  }
  case class SInteger[T]() extends SchemaType[T] {
    def show: String = "integer"
    override def contramap[TT](g: TT => T): SchemaType[TT] = SInteger()
  }
  case class SNumber[T]() extends SchemaType[T] {
    def show: String = "number"
    override def contramap[TT](g: TT => T): SchemaType[TT] = SNumber()
  }
  case class SBoolean[T]() extends SchemaType[T] {
    def show: String = "boolean"
    override def contramap[TT](g: TT => T): SchemaType[TT] = SBoolean()
  }
  case class SOption[T, E](element: Schema[E])(val toOption: T => Option[E]) extends SchemaType[T] {
    def show: String = s"option(${element.show})"
    override def contramap[TT](g: TT => T): SchemaType[TT] = SOption(element)(g.andThen(toOption))
  }
  case class SArray[T, E](element: Schema[E])(val toIterable: T => Iterable[E]) extends SchemaType[T] {
    def show: String = s"array(${element.show})"
    override def contramap[TT](g: TT => T): SchemaType[TT] = SArray(element)(g.andThen(toIterable))
  }
  case class SBinary[T]() extends SchemaType[T] {
    def show: String = "binary"
    override def contramap[TT](g: TT => T): SchemaType[TT] = SBinary()
  }
  case class SDate[T]() extends SchemaType[T] {
    def show: String = "date"
    override def contramap[TT](g: TT => T): SchemaType[TT] = SDate()
  }
  case class SDateTime[T]() extends SchemaType[T] {
    def show: String = "date-time"
    override def contramap[TT](g: TT => T): SchemaType[TT] = SDateTime()
  }

  sealed trait SObject[T] extends SchemaType[T] {
    def info: SObjectInfo
  }

  trait ProductField[T] {
    type FieldType
    def name: FieldName
    def get(t: T): Option[FieldType]
    def schema: Schema[FieldType]

    override def equals(other: Any): Boolean = other match { // TODO
      case p: ProductField[_] => p.name == name && p.schema == schema
      case _                  => false
    }

    override def toString: String = s"field($name,${schema})" // TODO
  }
  case class SProduct[T](info: SObjectInfo, fields: List[ProductField[T]]) extends SObject[T] {
    def required: List[FieldName] = fields.collect { case f if !f.schema.isOptional => f.name }
    def show: String = s"object(${fields.map(f => s"${f.name}->${f.schema.show}").mkString(",")}"
    override def contramap[TT](g: TT => T): SchemaType[TT] = SProduct(
      info,
      fields.map(f =>
        new ProductField[TT] {
          type FieldType = f.FieldType
          override val name: FieldName = f.name
          override def get(t: TT): Option[FieldType] = f.get(g(t))
          override val schema: Schema[FieldType] = f.schema
        }
      )
    )

    private[tapir] val fieldsWithValidation: List[ProductField[T]] = fields.collect {
      case f if f.schema.hasValidation => f
    }
  }
  object SProduct {
    def empty[T]: SProduct[T] = SProduct(SObjectInfo.Unit, Nil)
  }

  case class SOpenProduct[T, V](info: SObjectInfo, valueSchema: Schema[V])(val fieldValues: T => Map[String, V]) extends SObject[T] {
    override def show: String = s"map"
    override def contramap[TT](g: TT => T): SchemaType[TT] = SOpenProduct[TT, V](info, valueSchema)(g.andThen(fieldValues))
  }

  case class SCoproduct[T](info: SObjectInfo, schemas: SealedTrait[Schema, T], discriminator: Option[Discriminator]) extends SObject[T] {
    override def show: String = "oneOf:" + schemas.subtypes.values.mkString(",")

    def addDiscriminatorField[D](
        discriminatorName: FieldName,
        discriminatorSchema: Schema[D] = Schema.string,
        discriminatorMappingOverride: Map[String, SRef[_]] = Map.empty // TODO: used?
    ): SCoproduct[T] = {
      SCoproduct(
        info,
        new SealedTrait[Schema, T] {
          override def dispatch(t: T): String = schemas.dispatch(t)
          override val subtypes: Map[String, Schema[T]] = schemas.subtypes.mapValues {
            case s @ Schema(st: SchemaType.SProduct[T], _, _, _, _, _, _, _) =>
              s.copy(schemaType = st.copy(fields = st.fields :+ new ProductField[T] {
                override type FieldType = D
                override val name: FieldName = discriminatorName
                override def get(t: T): Option[D] = None
                override def schema: Schema[D] = discriminatorSchema
              }))
            case s => s
          }.toMap
        },
        Some(Discriminator(discriminatorName.encodedName, discriminatorMappingOverride))
      )
    }

    override def contramap[TT](g: TT => T): SchemaType[TT] = SCoproduct(
      info,
      new SealedTrait[Schema, TT] {
        override def dispatch(t: TT): String = schemas.dispatch(g(t))
        override def subtypes: Map[String, Schema[TT]] = schemas.subtypes.asInstanceOf[Map[String, Schema[TT]]]
      },
      discriminator
    )
  }

  case class SRef[T](info: SObjectInfo) extends SchemaType[T] {
    def show: String = s"ref($info)"
    override def contramap[TT](g: TT => T): SchemaType[TT] = SRef(info)
  }

  case class SObjectInfo(fullName: String, typeParameterShortNames: List[String] = Nil)
  object SObjectInfo {
    val Unit: SObjectInfo = SObjectInfo(fullName = "Unit")
  }

  case class Discriminator(propertyName: String, mappingOverride: Map[String, SRef[_]])
}
