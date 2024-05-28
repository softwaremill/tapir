package sttp.tapir.json.pickler

import _root_.upickle.implicits.{ReadersVersionSpecific, macros => upickleMacros}
import sttp.tapir.{Schema, SchemaType}

import scala.deriving.Mirror
import scala.reflect.ClassTag

/** A modification of upickle.implicits.Readers, implemented in order to provide our custom JSON decoding and typeclass derivation logic:
  *
  *   1. A CaseClassReader[T] is built based on readers for child fields passed as an argument, instead of just summoning these readers.
  *      This allows us to operate on Picklers and use readers extracted from these Picklers. Summoning is now done on Pickler, not Reader
  *      level.
  *   1. Default values can be passed as parameters, which are read from Schema annotations if present. Vanilla uPickle reads defaults only
  *      from case class defaults.
  *   1. Subtype discriminator can be passed as a parameter, allowing specyfing custom key for discriminator field, as well as function for
  *      extracting discriminator value.
  *   1. Schema is passed as a parameter, so that we can use its encodedName to transform field keys.
  *   1. Configuration can be used for setting discrtiminator field name or decoding all field names according to custom function (allowing
  *      transformations like snake_case, etc.)
  */
private[pickler] trait Readers extends ReadersVersionSpecific with UpickleHelpers {

  case class LeafWrapper[T](leaf: TaggedReader.Leaf[T], r: Reader[T], leafTagValue: String) extends TaggedReader[T] {
    override def findReader(s: String) = if (s == leafTagValue) r else null
  }

  override def annotate[V](rw: Reader[V], key: String, value: String) = {
    LeafWrapper(new TaggedReader.Leaf[V](key, value, rw), rw, value)
  }

  inline def macroProductR[T](
      schema: Schema[T],
      childReaders: Tuple,
      childDefaults: List[Option[Any]],
      m: Mirror.ProductOf[T],
      config: PicklerConfiguration
  ): Reader[T] =
    val schemaFields = schema.schemaType.asInstanceOf[SchemaType.SProduct[T]].fields

    val reader = new CaseClassReadereader[T](upickleMacros.paramsCount[T], upickleMacros.checkErrorMissingKeysCount[T]()) {
      override def visitors0 = childReaders
      override def fromProduct(p: Product): T = m.fromProduct(p)
      override def keyToIndex(x: String): Int =
        schemaFields.indexWhere(_.name.encodedName == x)

      override def allKeysArray = schemaFields.map(_.name.encodedName).toArray
      override def storeDefaults(x: _root_.upickle.implicits.BaseCaseObjectContext): Unit = {
        macros.storeDefaultsTapir[T](x, childDefaults)
      }
    }

    inline if upickleMacros.isSingleton[T] then
      annotate[T](SingletonReader[T](upickleMacros.getSingleton[T]), config.discriminator, upickleMacros.tagName[T])
    else if upickleMacros.isMemberOfSealedHierarchy[T] then annotate[T](reader, config.discriminator, upickleMacros.tagName[T])
    else reader

  inline def macroSumR[T](childPicklers: List[Pickler[_]], subtypeDiscriminator: SubtypeDiscriminator[T]): Reader[T] =
    implicit val currentlyDeriving: _root_.upickle.core.CurrentlyDeriving[T] = new _root_.upickle.core.CurrentlyDeriving()
    subtypeDiscriminator match {
      case discriminator: CustomSubtypeDiscriminator[T] =>
        // This part ensures that child product readers are replaced with product readers with proper "tag value".
        // This value is used by uPickle internals to find a matching reader for given discriminator value.
        // Originally product readers have this value set to class name when they are derived individually,
        // so we need to 'fix' them here using discriminator settings.
        val readersFromMapping = discriminator.mapping
          .map { case (k, v) => (k, v.innerUpickle.reader) }
          .map {
            case (k, leaf) if leaf.isInstanceOf[LeafWrapper[_]] =>
              TaggedReader
                .Leaf[T](discriminator.fieldName, discriminator.asString(k), leaf.asInstanceOf[LeafWrapper[_]].r.asInstanceOf[Reader[T]])
            case (_, otherKindOfReader) =>
              otherKindOfReader
          }

        new TaggedReader.Node[T](discriminator.fieldName, readersFromMapping.asInstanceOf[Seq[TaggedReader[T]]]: _*)

      case discriminator: DefaultSubtypeDiscriminator[T] =>
        val readers = childPicklers.map(cp => {
          (cp.schema.name, cp.innerUpickle.reader) match {
            case (Some(sName), wrappedReader: Readers#LeafWrapper[_]) =>
              TaggedReader.Leaf[T](
                discriminator.fieldName,
                discriminator.toValue(sName),
                wrappedReader.r.asInstanceOf[Reader[T]]
              )
            case _ =>
              cp.innerUpickle.reader.asInstanceOf[Reader[T]]
          }
        })
        Reader.merge(subtypeDiscriminator.fieldName, readers: _*)
    }
}
