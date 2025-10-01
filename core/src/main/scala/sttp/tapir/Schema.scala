package sttp.tapir

import sttp.model.{Part, Uri}
import sttp.tapir.Schema.{SName, Title}
import sttp.tapir.SchemaType._
import sttp.tapir.generic.{Configuration, Derived}
import sttp.tapir.internal.{ValidatorSyntax, isBasicValue}
import sttp.tapir.macros.{SchemaCompanionMacros, SchemaMacros}
import sttp.tapir.model.Delimited
import sttp.tapir.Schema.Explode
import sttp.tapir.Schema.Nullable
import sttp.tapir.Schema.Delimiter
import sttp.tapir.Schema.UniqueItems
import sttp.tapir.Schema.EncodedDiscriminatorValue

import java.io.InputStream
import java.math.{BigDecimal => JBigDecimal, BigInteger => JBigInteger}
import java.nio.ByteBuffer
import java.time._
import java.util.{Date, UUID}
import scala.annotation.{StaticAnnotation, implicitNotFound}

/** Describes the type `T`: its low-level representation, meta-data and validation rules.
  * @param format
  *   The name of the format of the low-level representation of `T`.
  */
@implicitNotFound(
  msg = """Could not find Schema for type ${T}.
Schemas can be derived automatically by adding: `import sttp.tapir.generic.auto._`, or manually using `Schema.derived[T]`.
The latter is also useful for debugging derivation errors.
You can find more details in the docs: https://tapir.softwaremill.com/en/latest/endpoint/schemas.html
When integrating with a third-party datatype library remember to import respective schemas/codecs as described in https://tapir.softwaremill.com/en/latest/endpoint/integrations.html"""
)
case class Schema[T](
    schemaType: SchemaType[T],
    name: Option[SName] = None,
    isOptional: Boolean = false,
    description: Option[String] = None,
    // The default value together with the value encoded to a raw format, which will then be directly rendered in the
    // documentation. This is needed as codecs for nested types aren't available. Similar to Validator.EncodeToRaw
    default: Option[(T, Option[Any])] = None,
    format: Option[String] = None,
    encodedExample: Option[Any] = None,
    deprecated: Boolean = false,
    hidden: Boolean = false,
    validator: Validator[T] = Validator.pass[T],
    attributes: AttributeMap = AttributeMap.Empty
) extends SchemaMacros[T] {

  def map[TT](f: T => Option[TT])(g: TT => T): Schema[TT] = copy(
    schemaType = schemaType.contramap(g),
    default = default.flatMap { case (t, raw) =>
      f(t).map(tt => (tt, raw))
    },
    validator = validator.contramap(g)
  )

  /** Adapt this schema to type `TT`. Only the meta-data is retained, except for default values and the validator (however, product
    * field/subtypes validators are retained). Run-time functionality:
    *   - traversing collection elements, product fields, or coproduct subtypes
    *   - validating an instance of type `TT` the top-level type is lost.
    */
  def as[TT]: Schema[TT] = copy(
    schemaType = schemaType.as[TT],
    default = None,
    validator = Validator.pass
  )

  /** Returns an optional version of this schema, with `isOptional` set to true. */
  def asOption: Schema[Option[T]] =
    Schema(
      schemaType = SOption(this)(identity),
      isOptional = true,
      format = format,
      deprecated = deprecated,
      hidden = hidden,
      attributes = attributes
    )

  /** Returns an array version of this schema, with the schema type wrapped in [[SArray]]. Sets `isOptional` to true as the collection might
    * be empty.
    */
  def asArray: Schema[Array[T]] =
    Schema(
      schemaType = SArray(this)(_.toIterable),
      isOptional = true,
      deprecated = deprecated,
      hidden = hidden,
      attributes = attributes
    )

  /** Returns a collection version of this schema, with the schema type wrapped in [[SArray]]. Sets `isOptional` to true as the collection
    * might be empty.
    */
  def asIterable[C[X] <: Iterable[X]]: Schema[C[T]] =
    Schema(
      schemaType = SArray(this)(identity),
      isOptional = true,
      deprecated = deprecated,
      hidden = hidden,
      attributes = attributes
    )

  def name(name: SName): Schema[T] = copy(name = Some(name))
  def name(name: Option[SName]): Schema[T] = copy(name = name)

  /** Renames a schema derived from generic class according to the instantiated type parameter.
    *
    * Due to some limitations of semi-automatic derivation, when a generic class `G[TT]` is instantiated to `G[A]`, the schema which is
    * automatically derived contains the name `G[TT]` instead of `G[A]`. This function adjusts `name.typeParameterShortNames` of the schema
    * to fix this.
    */
  def renameWithTypeParameter[TT](implicit stt: Schema[TT]): Schema[T] =
    name match {
      case None => throw new IllegalArgumentException("Generic class is nameless")
      case Some(name) =>
        stt.name match {
          case None          => throw new IllegalArgumentException("Type parameter of generic class is nameless")
          case Some(sttName) => copy(name = Some(SName(name.fullName, sttName.fullName :: sttName.typeParameterShortNames)))
        }
    }

  def description(d: String): Schema[T] = copy(description = Some(d))

  def encodedExample(e: Any): Schema[T] = copy(encodedExample = Some(e))

  /** Adds a default value, which is used by [[Codec]] s during decoding and for documentation.
    *
    * To represent the value in the documentation, an encoded form needs to be provided. The encoded form is inferred if missing and the
    * given value is of a basic type (number, string, etc.).
    */
  def default(t: T, encoded: Option[Any] = None): Schema[T] = {
    val raw2 = encoded match {
      case None if isBasicValue(t) => Some(t)
      case _                       => encoded
    }
    copy(default = Some((t, raw2)), isOptional = true)
  }

  def format(f: String): Schema[T] = copy(format = Some(f))

  def deprecated(d: Boolean): Schema[T] = copy(deprecated = d)

  def hidden(h: Boolean): Schema[T] = copy(hidden = h)

  /** Corresponds to JsonSchema's `title` parameter which should be used for defining title of the object. */
  def title(t: String): Schema[T] = attribute(Title.Attribute, Title(t))

  /** Corresponds to OpenAPI's `explode` parameter which should be used for delimited values.
    *
    * @see
    *   #delimiter
    */
  def explode(b: Boolean): Schema[T] = attribute(Explode.Attribute, Explode(b))

  /** Will override a schema's typing to include a `null` type, overriding the default behavior. */
  def nullable: Schema[T] = attribute(Nullable.Attribute, Nullable)

  /** Used in combination with `explode`, to properly represent delimited values in examples and default values. (#3581)
    *
    * @see
    *   #explode
    */
  def delimiter(delimiter: String): Schema[T] = attribute(Delimiter.Attribute, Delimiter(delimiter))

  def uniqueItems(uniqueItems: Boolean): Schema[T] = attribute(UniqueItems.Attribute, UniqueItems(uniqueItems))

  /** Specifies that the given schema is for a tuple. Tuples are products with no meaningful property names - attributes are identified by
    * their position.
    *
    * When converting a tuple schema of type [[SchemaType.SProduct]] to JSON schema, renders as an `array` schema, with type constraints for
    * each index (#3941).
    */
  def tuple: Schema[T] = attribute(Schema.Tuple.Attribute, Schema.Tuple(true))

  /** For coproduct schemas, when there's a discriminator field, used to attach the encoded value of the discriminator field. Such value is
    * added to the discriminator field schemas in each of the coproduct's subtypes. When rendering OpenAPI/JSON schema, these values are
    * converted to `const` constraints on fields.
    */
  def encodedDiscriminatorValue(v: String): Schema[T] = attribute(EncodedDiscriminatorValue.Attribute, EncodedDiscriminatorValue(v))

  def show: String = s"schema is $schemaType"

  def showValidators: Option[String] = {
    def showFieldValidators(fields: List[SProductField[T]]) = {
      fields.map(f => f.schema.showValidators.map(fvs => s"${f.name.name}->($fvs)")).collect { case Some(s) => s } match {
        case Nil => None
        case l   => Some(l.mkString(","))
      }
    }

    if (hasValidation) {
      val thisValidator = validator.show
      val childValidators = schemaType match {
        case SOption(element) => element.showValidators.map(esv => s"elements($esv)")
        case SArray(element)  => element.showValidators.map(esv => s"elements($esv)")
        case SProduct(fields) => showFieldValidators(fields)
        case SOpenProduct(fields, valueSchema) =>
          val fieldValidators = showFieldValidators(fields)
          val elementsValidators = valueSchema.showValidators.map(esv => s"elements($esv)")

          (fieldValidators, elementsValidators) match {
            case (None, None)       => None
            case (None, Some(_))    => elementsValidators
            case (Some(_), None)    => fieldValidators
            case (Some(f), Some(e)) => Some(f + " " + e)
          }

        case SCoproduct(subtypes, _) =>
          subtypes.map(s => s.showValidators.map(svs => s.name.fold(svs)(n => s"${n.show}->($svs)"))) match {
            case Nil => None
            case l   => Some(l.mkString(","))
          }
        case SRef(_) => Some("recursive")
        case _       => None
      }

      (thisValidator.toList ++ childValidators.toList) match {
        case Nil => None
        case l   => Some(l.mkString(","))
      }
    } else None
  }

  /** See [[modify]]: instead of a path expressed using code, accepts a path a sequence of `String`s. */
  def modifyUnsafe[U](fields: String*)(modify: Schema[U] => Schema[U]): Schema[T] = modifyAtPath(fields.toList, modify)

  private def modifyAtPath[U](fieldPath: List[String], modify: Schema[U] => Schema[U]): Schema[T] =
    fieldPath match {
      case Nil => modify(this.asInstanceOf[Schema[U]]).asInstanceOf[Schema[T]] // we don't have type-polymorphic functions
      case f :: fs =>
        def modifyFieldsAtPath(fields: List[SProductField[T]]) = {
          fields.map { field =>
            if (field.name.name == f) SProductField[T, field.FieldType](field.name, field.schema.modifyAtPath(fs, modify), field.get)
            else field
          }
        }

        val schemaType2 = schemaType match {
          case s @ SArray(element) if f == Schema.ModifyCollectionElements  => SArray(element.modifyAtPath(fs, modify))(s.toIterable)
          case s @ SOption(element) if f == Schema.ModifyCollectionElements => SOption(element.modifyAtPath(fs, modify))(s.toOption)
          case s @ SProduct(fields) =>
            s.copy(fields = modifyFieldsAtPath(fields))
          case s @ SOpenProduct(fields, valueSchema) if f == Schema.ModifyCollectionElements =>
            s.copy(
              fields = modifyFieldsAtPath(fields),
              valueSchema = valueSchema.modifyAtPath(fs, modify)
            )(s.mapFieldValues)
          case s @ SCoproduct(subtypes, _) =>
            s.copy(subtypes = subtypes.map(_.modifyAtPath(fieldPath, modify)))(s.subtypeSchema)
          case _ => schemaType
        }
        copy(schemaType = schemaType2)
    }

  /** Add a validator to this schema. If the validator contains a named enum validator:
    *   - the encode function is inferred if not yet defined, and the validators possible values are of a basic type
    *   - the name is set as the schema's name.
    */
  def validate(v: Validator[T]): Schema[T] = {
    val v2 = v.inferEnumerationEncode
    // if there's an enum validator, propagating the name of the enumeration to the schema
    v2.asPrimitiveValidators.collectFirst { case Validator.Enumeration(_, _, Some(name)) => name } match {
      case Some(name) => copy(name = Some(name), validator = validator.and(v2))
      case None       => copy(validator = validator.and(v2))
    }
  }

  /** Apply defined validation rules to the given value. */
  def applyValidation(t: T): List[ValidationError[?]] = applyValidation(t, Map())

  private def applyValidation(t: T, objects: Map[SName, Schema[?]]): List[ValidationError[?]] = {
    val objects2 = name.fold(objects)(n => objects + (n -> this))

    def applyFieldsValidation(fields: List[SProductField[T]]) = {
      fields.flatMap(f => f.get(t).map(f.schema.applyValidation(_, objects2)).getOrElse(Nil).map(_.prependPath(f.name)))
    }

    // we avoid running validation for structures where there are no validation rules applied (recursively)
    if (hasValidation) {
      validator(t) ++ (schemaType match {
        case s @ SOption(element) => s.toOption(t).toList.flatMap(element.applyValidation(_, objects2))
        case s @ SArray(element)  => s.toIterable(t).flatMap(element.applyValidation(_, objects2))
        case s @ SProduct(_)      => applyFieldsValidation(s.fieldsWithValidation)
        case s @ SOpenProduct(_, valueSchema) =>
          applyFieldsValidation(s.fieldsWithValidation) ++
            s.mapFieldValues(t).flatMap { case (k, v) => valueSchema.applyValidation(v, objects2).map(_.prependPath(FieldName(k, k))) }
        case s @ SCoproduct(_, _) =>
          s.subtypeSchema(t)
            .map { case SchemaWithValue(s, v) => s.applyValidation(v, objects2) }
            .getOrElse(Nil)
        case SRef(name) => objects.get(name).map(_.asInstanceOf[Schema[T]].applyValidation(t, objects2)).getOrElse(Nil)
        case _          => Nil
      })
    } else Nil
  }

  private[tapir] def hasValidation: Boolean = {
    (validator != Validator.pass) || (schemaType match {
      case SOption(element)                 => element.hasValidation
      case SArray(element)                  => element.hasValidation
      case s: SProduct[T]                   => s.fieldsWithValidation.nonEmpty
      case s @ SOpenProduct(_, valueSchema) => s.fieldsWithValidation.nonEmpty || valueSchema.hasValidation
      case SCoproduct(subtypes, _)          => subtypes.exists(_.hasValidation)
      case SRef(_)                          => true
      case _                                => false
    })
  }

  def attribute[A](k: AttributeKey[A]): Option[A] = attributes.get(k)
  def attribute[A](k: AttributeKey[A], v: A): Schema[T] = copy(attributes = attributes.put(k, v))
}

object Schema extends LowPrioritySchema with SchemaCompanionMacros {
  val ModifyCollectionElements = "each"

  /** Creates a schema for type `T`, where the low-level representation is a `String`. */
  def string[T]: Schema[T] = Schema(SString())

  /** Creates a schema for type `T`, where the low-level representation is binary. */
  def binary[T]: Schema[T] = Schema(SBinary())

  implicit val schemaForString: Schema[String] = Schema(SString())
  implicit val schemaForByte: Schema[Byte] = Schema(SInteger())
  implicit val schemaForShort: Schema[Short] = Schema(SInteger())
  implicit val schemaForInt: Schema[Int] = Schema(SInteger[Int]()).format("int32")
  implicit val schemaForLong: Schema[Long] = Schema(SInteger[Long]()).format("int64")
  implicit val schemaForFloat: Schema[Float] = Schema(SNumber[Float]()).format("float")
  implicit val schemaForDouble: Schema[Double] = Schema(SNumber[Double]()).format("double")
  implicit val schemaForBoolean: Schema[Boolean] = Schema(SBoolean())
  implicit val schemaForUnit: Schema[Unit] = Schema(SProduct.empty)
  implicit val schemaForFileRange: Schema[FileRange] = Schema(SBinary())
  implicit val schemaForByteArray: Schema[Array[Byte]] = Schema(SBinary())
  implicit val schemaForByteBuffer: Schema[ByteBuffer] = Schema(SBinary())
  implicit val schemaForInputStream: Schema[InputStream] = Schema(SBinary())
  implicit val schemaForInputStreamRange: Schema[InputStreamRange] = Schema(SchemaType.SBinary())

  // These are marked as lazy as they involve scala-java-time constructs, which massively inflate the bundle size for scala.js applications, even if
  // they are unused within the program. By marking them lazy, it allows them to be *tree shaken* and removed by the optimiser.
  implicit lazy val schemaForInstant: Schema[Instant] = Schema(SDateTime())
  implicit lazy val schemaForZonedDateTime: Schema[ZonedDateTime] = Schema(SDateTime())
  implicit lazy val schemaForOffsetDateTime: Schema[OffsetDateTime] = Schema(SDateTime())
  implicit lazy val schemaForDate: Schema[Date] = Schema(SDateTime())
  implicit lazy val schemaForLocalDateTime: Schema[LocalDateTime] = Schema(SString())
  implicit lazy val schemaForLocalDate: Schema[LocalDate] = Schema(SDate())
  implicit lazy val schemaForZoneOffset: Schema[ZoneOffset] = Schema(SString())
  implicit lazy val schemaForZoneId: Schema[ZoneId] = Schema(SString())
  implicit lazy val schemaForJavaDuration: Schema[Duration] = Schema(SString())
  implicit lazy val schemaForLocalTime: Schema[LocalTime] = Schema(SString())
  implicit lazy val schemaForOffsetTime: Schema[OffsetTime] = Schema(SString())

  implicit val schemaForScalaDuration: Schema[scala.concurrent.duration.Duration] = Schema(SString())
  implicit val schemaForUUID: Schema[UUID] = Schema(SString[UUID]()).format("uuid")
  implicit val schemaForBigDecimal: Schema[BigDecimal] = Schema(SNumber())
  implicit val schemaForJBigDecimal: Schema[JBigDecimal] = Schema(SNumber())
  implicit val schemaForBigInt: Schema[BigInt] = Schema(SInteger())
  implicit val schemaForJBigInteger: Schema[JBigInteger] = Schema(SInteger())
  implicit val schemaForFile: Schema[TapirFile] = Schema(SBinary())
  implicit val schemaForUri: Schema[Uri] = Schema(SString())
    .encodedExample(Uri("https", "example.com"))

  implicit def schemaForOption[T: Schema]: Schema[Option[T]] = implicitly[Schema[T]].asOption
  implicit def schemaForArray[T: Schema]: Schema[Array[T]] = implicitly[Schema[T]].asArray
  implicit def schemaForIterable[T: Schema, C[X] <: Iterable[X]]: Schema[C[T]] = implicitly[Schema[T]].asIterable[C]
  implicit def schemaForSet[T: Schema, C[X] <: scala.collection.Set[X]]: Schema[C[T]] =
    schemaForIterable[T, C].attribute(Schema.UniqueItems.Attribute, Schema.UniqueItems(true))
  implicit def schemaForPart[T: Schema]: Schema[Part[T]] = implicitly[Schema[T]].map(_ => None)(_.body)

  implicit def schemaForEither[A, B](implicit sa: Schema[A], sb: Schema[B]): Schema[Either[A, B]] = {
    Schema[Either[A, B]](
      SchemaType.SCoproduct(List(sa, sb), None) {
        case Left(v)  => Some(SchemaWithValue(sa, v))
        case Right(v) => Some(SchemaWithValue(sb, v))
      },
      for {
        na <- sa.name
        nb <- sb.name
      } yield Schema.SName(
        "Either",
        (na.fullName :: na.typeParameterShortNames) ++ (nb.fullName :: nb.typeParameterShortNames)
      )
    )
  }

  implicit def schemaForDelimited[D <: String, T](implicit tSchema: Schema[T]): Schema[Delimited[D, T]] =
    tSchema.asIterable[List].map(l => Some(Delimited[D, T](l)))(_.values).attribute(Explode.Attribute, Explode(false))

  /** @see Schema#explode */
  case class Explode(explode: Boolean)
  object Explode {
    val Attribute: AttributeKey[Explode] = new AttributeKey[Explode]("sttp.tapir.Schema.Explode")
  }

  /** @see Schema#nullable */
  object Nullable {
    val Attribute: AttributeKey[Nullable.type] = new AttributeKey[Nullable.type]("sttp.tapir.Schema.Nullable")
  }

  /** @see Schema#delimiter */
  case class Delimiter(delimiter: String)
  object Delimiter {
    val Attribute: AttributeKey[Delimiter] = new AttributeKey[Delimiter]("sttp.tapir.Schema.Delimiter")
  }

  /** @see Schema#title */
  case class Title(value: String)
  object Title {
    val Attribute: AttributeKey[Title] = new AttributeKey[Title]("sttp.tapir.Schema.Title")
  }

  /** @see Schema#uniqueItems */
  case class UniqueItems(uniqueItems: Boolean)
  object UniqueItems {
    val Attribute: AttributeKey[UniqueItems] = new AttributeKey[UniqueItems]("sttp.tapir.Schema.UniqueItems")
  }

  /** @see Schema#tuple */
  case class Tuple(isTuple: Boolean)
  object Tuple {
    val Attribute: AttributeKey[Tuple] = new AttributeKey[Tuple]("sttp.tapir.Schema.Tuple")
  }

  /** @see Schema#encodedDiscriminatorValue */
  case class EncodedDiscriminatorValue(v: String)
  object EncodedDiscriminatorValue {
    /*
    Implementation note: the discriminator value constraint is in fact an enum validator with a single possible enum value. Hence an
    alternative design would be to add such validators to discriminator fields, instead of an attribute. However, this has two drawbacks:
    1. when adding discriminator fields using `addDiscriminatorField`, we don't have access to the decoded discriminator value - only
       to the encoded one, via reverse mapping lookup
    2. the validator doesn't necessarily make sense, as it can't be used to validate the deserialiszd object. Usually the discriminator
       fields don't even exist on the high-level representations.
    That's why instead of re-using the validators, we decided to use a specialised attribute.
     */

    val Attribute: AttributeKey[EncodedDiscriminatorValue] =
      new AttributeKey[EncodedDiscriminatorValue]("sttp.tapir.Schema.EncodedDiscriminatorValue")
  }

  /** @param typeParameterShortNames
    *   full name of type parameters, name is legacy and kept only for backward compatibility
    */
  case class SName(fullName: String, typeParameterShortNames: List[String] = Nil) {
    def show: String = fullName + (if (typeParameterShortNames.isEmpty) "" else typeParameterShortNames.mkString("[", ",", "]"))
  }
  object SName {
    val Unit: SName = SName(fullName = "Unit")
  }

  /** Annotations which are used during automatic schema derivation, or semi-automatic schema derivation using [[Schema.derived]]. */
  object annotations {
    class description(val text: String) extends StaticAnnotation with Serializable
    class encodedExample(val example: Any) extends StaticAnnotation with Serializable
    class default[T](val default: T, val encoded: Option[Any] = None) extends StaticAnnotation with Serializable
    class format(val format: String) extends StaticAnnotation with Serializable
    class deprecated extends StaticAnnotation with Serializable
    class hidden extends StaticAnnotation with Serializable
    class encodedName(val name: String) extends StaticAnnotation with Serializable
    class title(val name: String) extends StaticAnnotation with Serializable

    /** Adds the `v` validator to the schema using [[Schema.validate]]. Note that the type of the validator must match exactly the type of
      * the class/field. This is not checked at compile-time, and might cause run-time exceptions. To validate elements of collections or
      * [[Option]]s, use [[validateEach]].
      */
    class validate[T](val v: Validator[T]) extends StaticAnnotation with Serializable

    /** Adds the `v` validators to elements of the schema, when the annotated class or field is a collection or [[Option]]. The type of the
      * validator must match exactly the type of the collection's elements. This is not checked at compile-time, and might cause run-time
      * exceptions. E.g. to validate that when an `Option[Int]` is defined, the value is smaller than 5, you should use:
      * {{{
      * case class Payload(
      *   @validateEach(Validator.max(4, exclusive = true))
      *   aField: Option[Int]
      * )
      * }}}
      */
    class validateEach[T](val v: Validator[T]) extends StaticAnnotation with Serializable
    class customise(val f: Schema[?] => Schema[?]) extends StaticAnnotation with Serializable
  }

  /** Wraps the given schema with a single-field product, where `fieldName` maps to `schema`.
    *
    * The resulting schema has no name.
    *
    * Useful when generating one-of schemas for coproducts, where to discriminate between child types a wrapper product is used. To
    * automatically derive such a schema for a sealed hierarchy, see [[Schema.oneOfWrapped]].
    */
  def wrapWithSingleFieldProduct[T](schema: Schema[T], fieldName: FieldName): Schema[T] = Schema(
    schemaType = SchemaType.SProduct(List(SchemaType.SProductField[T, T](fieldName, schema, t => Some(t))))
  )

  /** Wraps the given schema with a single-field product, where a field computed using the implicit [[Configuration]] maps to `schema`.
    *
    * The resulting schema has no name.
    *
    * Useful when generating one-of schemas for coproducts, where to discriminate between child types a wrapper product is used. To
    * automatically derive such a schema for a sealed hierarchy, see [[Schema.oneOfWrapped]].
    */
  def wrapWithSingleFieldProduct[T](schema: Schema[T])(implicit conf: Configuration): Schema[T] =
    wrapWithSingleFieldProduct(schema, FieldName(conf.toDiscriminatorValue(schema.name.getOrElse(SName.Unit))))

  /** A schema allowing anything: a number, string, object, etc. A [[SCoproduct]] with no specified subtypes.
    * @see
    *   [[anyObject]]
    */
  def any[T]: Schema[T] = Schema(SCoproduct(Nil, None)(_ => None), None)

  /** A schema allowing any object. A [[SProduct]] with no specified fields.
    * @see
    *   [[any]]
    */
  def anyObject[T]: Schema[T] = Schema(SProduct(Nil), None)
}

trait LowPrioritySchema {
  implicit def derivedSchema[T](implicit derived: Derived[Schema[T]]): Schema[T] = derived.value
}
