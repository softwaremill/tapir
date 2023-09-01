package sttp.tapir.json

import sttp.tapir.Codec.JsonCodec
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

trait TapirPickle[T] extends Readers with Writers:
  def reader: this.Reader[T]
  def writer: this.Writer[T]

abstract class TapirPickleBase[T] extends TapirPickle[T]

class DefaultReadWriterWrapper[T](delegateDefault: _root_.upickle.default.ReadWriter[T]) extends TapirPickleBase[T]:
  lazy val rw: this.ReadWriter[T] = new ReadWriter[T] {
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
  override lazy val reader = rw
  override lazy val writer = rw

case class Pickler[T](innerUpickle: TapirPickle[T], schema: Schema[T]):
  def toCodec: JsonCodec[T] = {
    import innerUpickle._
    given reader: innerUpickle.Reader[T] = innerUpickle.reader
    given writer: innerUpickle.Writer[T] = innerUpickle.writer
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
    given subtypeDiscriminator: SubtypeDiscriminator[T] = DefaultSubtypeDiscriminator()
    summonFrom {
      case schema: Schema[T] => fromExistingSchemaAndRw[T](schema)
      case _                 => buildNewPickler[T]()
    }

  inline def oneOfUsingField[T: ClassTag, V](extractor: T => V, asString: V => String)(
      mapping: (V, Pickler[_ <: T])*
  )(using m: Mirror.Of[T], c: Configuration, p: Pickler[V]): Pickler[T] =

    val paramExtractor = extractor
    val paramAsString = asString
    val paramMapping = mapping
    type ParamV = V
    given subtypeDiscriminator: SubtypeDiscriminator[T] = new CustomSubtypeDiscriminator[T] {
      type V = ParamV
      override def extractor = paramExtractor
      override def asString = paramAsString
      override lazy val mapping = paramMapping
    }
    summonFrom {
      case schema: Schema[T] => fromExistingSchemaAndRw[T](schema)
      case _ =>
        inline m match {
          case p: Mirror.ProductOf[T] =>
            error(
              s"Unexpected product type (case class) ${implicitly[ClassTag[T]].runtimeClass.getSimpleName()}, this method should only be used with sum types (like sealed hierarchy)"
            )
          case s: Mirror.SumOf[T] =>
            inline if (isScalaEnum[T])
              error("oneOfUsingField cannot be used with enums. Try Pickler.derivedEnumeration instead.")
            else {
              given schemaV: Schema[V] = p.schema
              val schema: Schema[T] = Schema.oneOfUsingField[T, V](extractor, asString)(
                mapping.toList.map { case (v, p) =>
                  (v, p.schema)
                }: _*
              )
              lazy val childPicklers: Tuple.Map[m.MirroredElemTypes, Pickler] = summonChildPicklerInstances[T, m.MirroredElemTypes]
              picklerSum(schema, s, childPicklers)
            }
        }
    }

  implicit inline def primitivePickler[T](using Configuration, NotGiven[Mirror.Of[T]]): Pickler[T] =
    Pickler(new DefaultReadWriterWrapper(summonInline[_root_.upickle.default.ReadWriter[T]]), summonInline[Schema[T]])

  private inline def errorForType[T](inline template: String): Unit = ${ errorForTypeImpl[T]('template) }

  import scala.quoted.*
  private def errorForTypeImpl[T: Type](template: Expr[String])(using Quotes): Expr[Unit] = {
    import quotes.reflect.*
    val templateStr = template.valueOrAbort
    val typeName = TypeRepr.of[T].show
    report.error(String.format(templateStr, typeName))
    '{}
  }

  private inline def fromExistingSchemaAndRw[T](schema: Schema[T])(using ClassTag[T], Configuration, Mirror.Of[T]): Pickler[T] =
    summonFrom {
      case foundRW: _root_.upickle.default.ReadWriter[T] => // there is BOTH schema and ReadWriter in scope
        new Pickler[T](new DefaultReadWriterWrapper(foundRW), schema)
      case _ =>
        errorForType[T](
          "Found implicit Schema[%s] but couldn't find a uPickle ReadWriter for this type. Either provide a ReadWriter, or remove the Schema from scope and let Pickler derive its own."
        )
        null
    }

  private inline def buildNewPickler[T: ClassTag](
  )(using m: Mirror.Of[T], c: Configuration, subtypeDiscriminator: SubtypeDiscriminator[T]): Pickler[T] =
    // The lazy modifier is necessary for preventing infinite recursion in the derived instance for recursive types such as Lst
    lazy val childPicklers: Tuple.Map[m.MirroredElemTypes, Pickler] = summonChildPicklerInstances[T, m.MirroredElemTypes]
    inline m match {
      case p: Mirror.ProductOf[T] => picklerProduct(p, childPicklers)
      case s: Mirror.SumOf[T] =>
        val schema: Schema[T] =
          inline if (isScalaEnum[T])
            Schema.derivedEnumeration[T].defaultStringBased
          else
            Schema.derived[T]
        picklerSum(schema, s, childPicklers)
    }

private inline def summonChildPicklerInstances[T: ClassTag, Fields <: Tuple](using
    m: Mirror.Of[T],
    c: Configuration
): Tuple.Map[Fields, Pickler] =
  inline erasedValue[Fields] match {
    case _: (fieldType *: fieldTypesTail) =>
      val processedHead = deriveOrSummon[T, fieldType]
      val processedTail = summonChildPicklerInstances[T, fieldTypesTail]
      Tuple.fromArray((processedHead +: processedTail.toArray)).asInstanceOf[Tuple.Map[Fields, Pickler]]
    case _: EmptyTuple.type => EmptyTuple.asInstanceOf[Tuple.Map[Fields, Pickler]]
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
private inline def picklerProduct[T: ClassTag, TFields <: Tuple](
    inline product: Mirror.ProductOf[T],
    childPicklers: => Tuple.Map[TFields, Pickler]
)(using
    config: Configuration,
    subtypeDiscriminator: SubtypeDiscriminator[T]
): Pickler[T] =
  lazy val childSchemas: Tuple.Map[TFields, Schema] =
    childPicklers.map([t] => (p: t) => p.asInstanceOf[Pickler[t]].schema).asInstanceOf[Tuple.Map[TFields, Schema]]
  val schema: Schema[T] = productSchema(product, childSchemas)
  val tapirPickle = new TapirPickle[T] {
    override def tagName = config.discriminator.getOrElse(super.tagName)

    override lazy val writer: Writer[T] =
      macroProductW[T](
        schema,
        childPicklers.map([a] => (obj: a) => obj.asInstanceOf[Pickler[a]].innerUpickle.writer).productIterator.toList,
        subtypeDiscriminator
      )
    override lazy val reader: Reader[T] =
      macroProductR[T](schema, childPicklers.map([a] => (obj: a) => obj.asInstanceOf[Pickler[a]].innerUpickle.reader))(using product)

  }
  Pickler[T](tapirPickle, schema)

private inline def productSchema[T, TFields <: Tuple](product: Mirror.ProductOf[T], childSchemas: Tuple.Map[TFields, Schema])(using
    genericDerivationConfig: Configuration
): Schema[T] =
  macros.SchemaDerivation2.productSchema(genericDerivationConfig, childSchemas)

private inline def picklerSum[T: ClassTag, CP <: Tuple](schema: Schema[T], s: Mirror.SumOf[T], childPicklers: => CP)(using
    m: Mirror.Of[T],
    config: Configuration,
    subtypeDiscriminator: SubtypeDiscriminator[T]
): Pickler[T] =
  val tapirPickle = new TapirPickle[T] {
    override def tagName = config.discriminator.getOrElse(super.tagName)
    override lazy val writer: Writer[T] =
      macroSumW[T](
        schema,
        childPicklers.map([a] => (obj: a) => obj.asInstanceOf[Pickler[a]].innerUpickle.writer).productIterator.toList,
        subtypeDiscriminator
      )
    override lazy val reader: Reader[T] =
      macroSumR[T](childPicklers.map([a] => (obj: a) => obj.asInstanceOf[Pickler[a]].innerUpickle.reader), subtypeDiscriminator)

  }
  new Pickler[T](tapirPickle, schema)

implicit def picklerToCodec[T](using p: Pickler[T]): JsonCodec[T] = p.toCodec

transparent inline def isScalaEnum[X]: Boolean = inline compiletime.erasedValue[X] match
  case _: Null         => false
  case _: Nothing      => false
  case _: reflect.Enum => true
  case _               => false

object generic {
  object auto { // TODO move to appropriate place
    inline implicit def picklerForCaseClass[T: ClassTag](implicit m: Mirror.Of[T], cfg: Configuration): Pickler[T] = Pickler.derived[T]
  }

}
