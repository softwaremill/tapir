package sttp.tapir.json.pickler

import sttp.tapir.generic.Configuration
import sttp.tapir.macros.CreateDerivedEnumerationSchema
import sttp.tapir.{Schema, SchemaAnnotations, SchemaType, Validator}

import scala.deriving.Mirror
import scala.reflect.ClassTag

private[pickler] class CreateDerivedEnumerationPickler[T: ClassTag](
    validator: Validator.Enumeration[T],
    schemaAnnotations: SchemaAnnotations[T]
):

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

  inline def defaultStringBased(using Mirror.Of[T]) = apply()

  inline def customStringBased(encode: T => String)(using Mirror.Of[T]): Pickler[T] =
    apply(
      Some(encode),
      schemaType = SchemaType.SString[T](),
      default = None
    )
