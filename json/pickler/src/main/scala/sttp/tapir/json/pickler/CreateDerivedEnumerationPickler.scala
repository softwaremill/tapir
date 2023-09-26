package sttp.tapir.json.pickler

import sttp.tapir.generic.Configuration
import sttp.tapir.macros.CreateDerivedEnumerationSchema
import sttp.tapir.{Schema, SchemaAnnotations, SchemaType, Validator}

import scala.deriving.Mirror
import scala.reflect.ClassTag

/** A builder allowing deriving Pickler for enums, used by [[Pickler.derivedEnumeration]]. Can be used to set non-standard encoding logic,
  * schema type or default value for an enum.
  */
class CreateDerivedEnumerationPickler[T: ClassTag](
    validator: Validator.Enumeration[T],
    schemaAnnotations: SchemaAnnotations[T]
):

  /** @param encode
    *   Specify how values of this type can be encoded to a raw value (typically a [[String]]; the raw form should correspond with
    *   `schemaType`). This encoding will be used when writing/reading JSON and generating documentation. Defaults to an identity function,
    *   which effectively means that `.toString` will be used to represent the enumeration in the docs.
    * @param schemaType
    *   The low-level representation of the enumeration. Defaults to a string.
    */
  inline def apply(
      encode: Option[T => Any] = Some(identity),
      schemaType: SchemaType[T] = SchemaType.SString[T](),
      default: Option[T] = None
  )(using m: Mirror.Of[T]): Pickler[T] = {
    val schema: Schema[T] = new CreateDerivedEnumerationSchema(validator, schemaAnnotations).apply(
      encode,
      schemaType,
      default
    )
    given Configuration = Configuration.default
    given SubtypeDiscriminator[T] = EnumValueDiscriminator[T](
      encode.map(_.andThen(_.toString)).getOrElse(_.toString),
      validator
    )
    lazy val childPicklers: Tuple.Map[m.MirroredElemTypes, Pickler] = Pickler.summonChildPicklerInstances[T, m.MirroredElemTypes]
    Pickler.picklerSum(schema, childPicklers)
  }

  /** Creates the Pickler assuming the low-level representation is a `String`. The encoding function passes the object unchanged (which
    * means `.toString` will be used to represent the enumeration in JSON and documentation). Typically you don't need to explicitly use
    * `Pickler.derivedEnumeration[T].defaultStringBased`, as this is the default behavior of [[Pickler.derived]] for enums.
    */
  inline def defaultStringBased(using Mirror.Of[T]) = apply()

  /** Creates the Pickler assuming the low-level representation is a `String`. Provide your custom encoding function for representing an
    * enum value as a String. It will be used to represent the enumeration in JSON and documentation. This approach is recommended if you
    * need to encode enums using a common field in their base trait, or another specific logic for extracting string representation.
    */
  inline def customStringBased(encode: T => String)(using Mirror.Of[T]): Pickler[T] =
    apply(
      Some(encode),
      schemaType = SchemaType.SString[T](),
      default = None
    )
