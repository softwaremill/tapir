package sttp.tapir

import sttp.tapir.internal.IterableToListMap

import scala.collection.immutable.ListMap

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

  trait SProductField[T] {
    type FieldType
    def name: FieldName
    def schema: Schema[FieldType]
    def get: T => Option[FieldType]

    override def equals(other: Any): Boolean = other match {
      case p: SProductField[_] => p.name == name && p.schema == schema
      case _                   => false
    }

    def show: String = s"field($name,${schema.show})"
    override def toString: String = s"SProductField($name,$schema)"
  }
  object SProductField {
    def apply[T, F](_name: FieldName, _schema: Schema[F], _get: T => Option[F]): SProductField[T] = new SProductField[T] {
      override type FieldType = F
      override val name: FieldName = _name
      override val schema: Schema[F] = _schema
      override val get: T => Option[F] = _get
    }
  }
  case class SProduct[T](info: SObjectInfo, fields: List[SProductField[T]]) extends SObject[T] {
    def required: List[FieldName] = fields.collect { case f if !f.schema.isOptional => f.name }
    def show: String = s"object(${fields.map(f => s"${f.name}->${f.schema.show}").mkString(",")}"
    override def contramap[TT](g: TT => T): SchemaType[TT] = SProduct(
      info,
      fields.map(f => SProductField[TT, f.FieldType](f.name, f.schema, g.andThen(f.get)))
    )

    private[tapir] val fieldsWithValidation: List[SProductField[T]] = fields.collect {
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

  case class SCoproduct[T](info: SObjectInfo, subtypes: ListMap[SObjectInfo, Schema[_]], discriminator: Option[SDiscriminator])(
      val subtypeInfo: T => Option[SObjectInfo]
  ) extends SObject[T] {
    override def show: String = "oneOf:" + subtypes.values.mkString(",")

    def addDiscriminatorField[D](
        discriminatorName: FieldName,
        discriminatorSchema: Schema[D] = Schema.string,
        discriminatorMapping: Map[String, SRef[_]] = Map.empty
    ): SCoproduct[T] = {
      SCoproduct(
        info,
        subtypes.mapValues {
          case s @ Schema(st: SchemaType.SProduct[T], _, _, _, _, _, _, _) =>
            s.copy(schemaType = st.copy(fields = st.fields :+ SProductField[T, D](discriminatorName, discriminatorSchema, _ => None)))
          case s => s
        }.toListMap,
        Some(SDiscriminator(discriminatorName, discriminatorMapping))
      )(subtypeInfo)
    }

    override def contramap[TT](g: TT => T): SchemaType[TT] = SCoproduct(
      info,
      subtypes,
      discriminator
    )(g.andThen(subtypeInfo))
  }

  case class SRef[T](info: SObjectInfo) extends SchemaType[T] {
    def show: String = s"ref($info)"
    override def contramap[TT](g: TT => T): SchemaType[TT] = SRef(info)
  }

  case class SObjectInfo(fullName: String, typeParameterShortNames: List[String] = Nil)
  object SObjectInfo {
    val Unit: SObjectInfo = SObjectInfo(fullName = "Unit")
  }

  /** @param mapping Schemas that should be used, given the `name` field's value. */
  case class SDiscriminator(name: FieldName, mapping: Map[String, SRef[_]])
}
