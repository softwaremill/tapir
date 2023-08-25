package sttp.tapir.json

import sttp.tapir.Codec.JsonCodec
import _root_.upickle.AttributeTagged
import sttp.tapir.Schema
import sttp.tapir.Codec
import scala.util.Try
import scala.util.Success
import sttp.tapir.DecodeResult.Error
import sttp.tapir.DecodeResult.Value
import scala.util.Failure
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import _root_.upickle.core.Visitor
import _root_.upickle.core.ObjVisitor
import _root_.upickle.core.ArrVisitor
import scala.compiletime.*
import scala.deriving.Mirror
import scala.util.NotGiven
import scala.reflect.ClassTag
import sttp.tapir.generic.Configuration
import _root_.upickle.core.*
import _root_.upickle.implicits.{macros => upickleMacros}

trait TapirPickle[T] extends Readers with Writers:
  def rw: this.ReadWriter[T]

abstract class TapirPickleBase[T] extends TapirPickle[T]

class DefaultReadWriterWrapper[T](delegateDefault: _root_.upickle.default.ReadWriter[T]) extends TapirPickleBase[T]:
  def rw: this.ReadWriter[T] = new ReadWriter[T] {

    override def visitArray(length: Int, index: Int): ArrVisitor[Any, T] = delegateDefault.visitArray(length, index)

    override def visitFloat64String(s: String, index: Int): T = delegateDefault.visitFloat64String(s, index)

    override def visitFloat32(d: Float, index: Int): T = delegateDefault.visitFloat32(d, index)

    override def visitObject(length: Int, jsonableKeys: Boolean, index: Int): ObjVisitor[Any, T] =
      delegateDefault.visitObject(length, jsonableKeys, index)

    override def visitFloat64(d: Double, index: Int): T = delegateDefault.visitFloat64(d, index)

    override def visitInt32(i: Int, index: Int): T = delegateDefault.visitInt32(i, index)

    override def visitInt64(i: Long, index: Int): T = delegateDefault.visitInt64(i, index)

    override def write0[V](out: Visitor[?, V], v: T): V = delegateDefault.write0(out, v)

    override def visitBinary(bytes: Array[Byte], offset: Int, len: Int, index: Int): T =
      delegateDefault.visitBinary(bytes, offset, len, index)

    override def visitExt(tag: Byte, bytes: Array[Byte], offset: Int, len: Int, index: Int): T =
      delegateDefault.visitExt(tag, bytes, offset, len, index)

    override def visitNull(index: Int): T = delegateDefault.visitNull(index)

    override def visitChar(s: Char, index: Int): T = delegateDefault.visitChar(s, index)

    override def visitFalse(index: Int): T = delegateDefault.visitFalse(index)

    override def visitString(s: CharSequence, index: Int): T = delegateDefault.visitString(s, index)

    override def visitTrue(index: Int): T = delegateDefault.visitTrue(index)

    override def visitFloat64StringParts(s: CharSequence, decIndex: Int, expIndex: Int, index: Int): T =
      delegateDefault.visitFloat64StringParts(s, decIndex, expIndex, index)

    override def visitUInt64(i: Long, index: Int): T = delegateDefault.visitUInt64(i, index)
  }

case class Pickler[T](innerUpickle: TapirPickle[T], schema: Schema[T]):
  def toCodec: JsonCodec[T] = {
    import innerUpickle._
    given readWriter: innerUpickle.ReadWriter[T] = innerUpickle.rw
    given schemaT: Schema[T] = schema
    Codec.json[T] { s =>
      Try(read[T](s)) match {
        case Success(v) => Value(v)
        case Failure(e) => Error(s, JsonDecodeException(errors = List.empty, e))
      }
    } { t => write(t) }
  }

object Pickler:
  inline def derived[T: ClassTag](using Configuration, Mirror.Of[T]): Pickler[T] =
    println(s">>>>>>>>>>> building new pickler for type ${implicitly[ClassTag[T]].getClass().getSimpleName()}")
    summonFrom {
      case schema: Schema[T] => fromExistingSchema[T](schema)
      case _                 => fromMissingSchema[T]
    }

  private inline def fromMissingSchema[T: ClassTag](using Configuration, Mirror.Of[T]): Pickler[T] =
    // can badly affect perf, it's going to repeat derivation excessively
    // the issue here is that deriving writers for nested CC fields requires schemas for these field types, and deriving each
    // such schema derives all of its childschemas. Another problem is delivering schemas for the same type many times
    given schema: Schema[T] = Schema.derived
    fromExistingSchema(schema)

  implicit inline def primitivePickler[T](using Configuration, NotGiven[Mirror.Of[T]]): Pickler[T] =
    Pickler(new DefaultReadWriterWrapper(summonInline[_root_.upickle.default.ReadWriter[T]]), summonInline[Schema[T]])

  private inline def fromExistingSchema[T: ClassTag](schema: Schema[T])(using Configuration, Mirror.Of[T]): Pickler[T] =
    summonFrom {
      case foundRW: _root_.upickle.default.ReadWriter[T] => // there is BOTH schema and ReadWriter in scope
        new Pickler[T](new DefaultReadWriterWrapper(foundRW), schema)
      case _ =>
        buildReadWritersFromSchema(schema)
    }

  private inline def buildReadWritersFromSchema[T: ClassTag](schema: Schema[T])(using m: Mirror.Of[T], c: Configuration): Pickler[T] =
    println(s">>>>>> Building new pickler for ${schema.name}")
    // The lazy modifier is necessary for preventing infinite recursion in the derived instance for recursive types such as Lst
    lazy val childPicklers = summonChildPicklerInstances[T, m.MirroredElemTypes]
    inline m match {
      case p: Mirror.ProductOf[T] => picklerProduct(p, schema, childPicklers)
      case s: Mirror.SumOf[T]     => picklerSum(s, schema, childPicklers)
    }

  private inline def summonChildPicklerInstances[T: ClassTag, Fields <: Tuple](using
      m: Mirror.Of[T],
      c: Configuration
  ): Tuple =
    inline erasedValue[Fields] match {
      case _: (fieldType *: fieldTypesTail) =>
        deriveOrSummon[T, fieldType] *: summonChildPicklerInstances[T, fieldTypesTail]
      case _: EmptyTuple => EmptyTuple
    }

  private inline def deriveOrSummon[T, FieldType](using Configuration): Pickler[FieldType] =
    inline erasedValue[FieldType] match
      case _: T => deriveRec[T, FieldType]
      case _    => summonInline[Pickler[FieldType]]

  private inline def deriveRec[T, FieldType](using config: Configuration): Pickler[FieldType] =
    inline erasedValue[T] match
      case _: FieldType => error("Infinite recursive derivation")
      case _            => Pickler.derived[FieldType](using summonInline[ClassTag[FieldType]], config, summonInline[Mirror.Of[FieldType]])

      // Extract child RWs from child picklers
      // create a new RW from scratch using children rw and fields of the product
      // use provided existing schema
      // use data from schema to customize the new schema
  private inline def picklerProduct[T: ClassTag, CP <: Tuple](product: Mirror.ProductOf[T], schema: Schema[T], childPicklers: => CP)(using
      Configuration
  ): Pickler[T] =
    println(s">>>>>>> pickler product for ${schema.name}")
    val tapirPickle = new TapirPickle[T] {
      lazy val writer: Writer[T] =
        macroProductW[T](schema, childPicklers.map([a] => (obj: a) => obj.asInstanceOf[Pickler[a]].innerUpickle.rw).productIterator.toList)
      lazy val reader: Reader[T] = macroProductR[T](childPicklers.map([a] => (obj: a) => obj.asInstanceOf[Pickler[a]].innerUpickle.rw))(using product)

      override def rw: ReadWriter[T] = ReadWriter.join(reader, writer)
    }
    new Pickler[T](tapirPickle, schema)

  private inline def picklerSum[T: ClassTag, CP <: Tuple](s: Mirror.SumOf[T], schema: Schema[T], childPicklers: => CP): Pickler[T] =
    new Pickler[T](null, schema) // TODO

  implicit def picklerToCodec[T](using p: Pickler[T]): JsonCodec[T] = p.toCodec

object generic {
  object auto { // TODO move to appropriate place
    inline implicit def picklerForCaseClass[T: ClassTag](implicit m: Mirror.Of[T], cfg: Configuration): Pickler[T] = Pickler.derived[T]
  }
}
