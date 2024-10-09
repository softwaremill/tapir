package sttp.tapir.json.pickler

import _root_.upickle.implicits.{macros => upickleMacros}
import sttp.tapir.macros.CreateDerivedEnumerationSchema
import sttp.tapir.{Schema, SchemaAnnotations, SchemaType, Validator}
import upickle.core.{Annotator, Types}

import scala.deriving.Mirror
import scala.reflect.ClassTag

import compiletime.*

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
      encode: T => Any = identity,
      schemaType: SchemaType[T] = SchemaType.SString[T](),
      default: Option[T] = None
  )(using m: Mirror.SumOf[T]): Pickler[T] = {
    val schema: Schema[T] = new CreateDerivedEnumerationSchema(validator, schemaAnnotations).apply(
      Some(encode),
      schemaType,
      default
    )
    lazy val childReadWriters = buildEnumerationReadWriters[T, m.MirroredElemTypes]
    val tapirPickle = new TapirPickle[T] {
      override lazy val reader: Reader[T] = {
        val readersForPossibleValues: Seq[TaggedReader[T]] =
          childReadWriters.map { case (enumValue, reader, _) =>
            TaggedReader.Leaf[T](encode(enumValue.asInstanceOf[T]).toString, reader.asInstanceOf[LeafWrapper[_]].r.asInstanceOf[Reader[T]])
          }
        new TaggedReader.Node[T](readersForPossibleValues: _*)
      }

      override lazy val writer: Writer[T] =
        new TaggedWriter.Node[T](childReadWriters.map(_._3.asInstanceOf[TaggedWriter[T]]): _*) {
          override def findWriterWithKey(v: Any): (String, String, ObjectWriter[T]) =
            val (tagKey, tagValue, writer) = super.findWriterWithKey(v)
            // Here our custom encoding transforms the value of a singleton object
            val overriddenTag = encode(v.asInstanceOf[T]).toString
            (tagKey, overriddenTag, writer)
        }
    }
    new Pickler[T](tapirPickle, schema)
  }

  private inline def buildEnumerationReadWriters[T: ClassTag, Cases <: Tuple]: List[(Any, Types#Reader[_], Types#Writer[_])] =
    inline erasedValue[Cases] match {
      case _: (enumerationCase *: enumerationCasesTail) =>
        val (reader, writer) = readWriterForEnumerationCase[enumerationCase]
        val processedTail = buildEnumerationReadWriters[T, enumerationCasesTail]
        ((productValue[enumerationCase], reader, writer) +: processedTail)
      case _: EmptyTuple.type => Nil
    }

  private inline def productValue[E] = summonFrom { case m: Mirror.ProductOf[E] => m.fromProduct(EmptyTuple) }

  /** Enumeration cases and case objects in an enumeration need special writers and readers, which are generated here, instead of being
    * taken from child picklers. For example, for enum Color and case values Red and Blue, a Writer should just use the object Red or Blue
    * and serialize it to "Red" or "Blue". If user needs to encode the singleton object using a custom function, this happens on a higher
    * level - the top level of coproduct reader and writer.
    */
  private inline def readWriterForEnumerationCase[C]: (Types#Reader[C], Types#Writer[C]) =
    val pickle = new TapirPickle[C] {
      // We probably don't need a separate TapirPickle for each C, this could be optimized.
      // https://github.com/softwaremill/tapir/issues/3192
      override lazy val writer = annotate[C](
        SingletonWriter[C](null.asInstanceOf[C]),
        Annotator.defaultTagKey, // not used in enumerations
        upickleMacros.tagName[C],
        Annotator.Checker.Val(upickleMacros.getSingleton[C])
      )
      override lazy val reader = annotate[C](
        SingletonReader[C](upickleMacros.getSingleton[C]),
        Annotator.defaultTagKey, // not used in enumerations
        upickleMacros.tagName[C]
      )
    }
    (pickle.reader, pickle.writer)

  /** Creates the Pickler assuming the low-level representation is a `String`. The encoding function passes the object unchanged (which
    * means `.toString` will be used to represent the enumeration in JSON and documentation). Typically you don't need to explicitly use
    * `Pickler.derivedEnumeration[T].defaultStringBased`, as this is the default behavior of [[Pickler.derived]] for enums.
    */
  inline def defaultStringBased(using Mirror.SumOf[T]) = apply()

  /** Creates the Pickler assuming the low-level representation is a `String`. Provide your custom encoding function for representing an
    * enum value as a String. It will be used to represent the enumeration in JSON and documentation. This approach is recommended if you
    * need to encode enums using a common field in their base trait, or another specific logic for extracting string representation.
    */
  inline def customStringBased(encode: T => String)(using Mirror.SumOf[T]): Pickler[T] =
    apply(
      encode,
      schemaType = SchemaType.SString[T](),
      default = None
    )
