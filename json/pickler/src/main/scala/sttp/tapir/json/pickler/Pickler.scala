package sttp.tapir.json.pickler

import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.SchemaType.SProduct
import sttp.tapir.generic.Configuration
import sttp.tapir.internal.EnumerationMacros.*
import sttp.tapir.json.pickler.SubtypeDiscriminator
import sttp.tapir.{Codec, Schema, SchemaAnnotations, Validator}
import upickle.Api
import upickle.core.Types

import java.math.{BigDecimal as JBigDecimal, BigInteger as JBigInteger}
import scala.annotation.implicitNotFound
import scala.collection.Factory
import scala.compiletime.*
import scala.deriving.Mirror
import scala.quoted.*
import scala.reflect.ClassTag
import scala.util.{Failure, NotGiven, Success, Try}

import TapirPickle._
import macros.*

object Pickler:

  /** Derive a [[Pickler]] instance for the given type, at compile-time. Depending on the derivation mode (auto / semi-auto), picklers for
    * referenced types (e.g. via a field, enum case or subtype) will either be derived automatically, or will need to be provided manually.
    *
    * This method can either be used explicitly, in the definition of a `given`, or indirectly by adding a `... derives Pickler` modifier to
    * a datatype definition.
    *
    * The in-scope [[PicklerConfiguration]] instance is used to customise field names and other behavior.
    */
  inline def derived[T: ClassTag](using PicklerConfiguration, Mirror.Of[T]): Pickler[T] =
    summonFrom {
      case schema: Schema[T] => fromExistingSchemaAndRw[T](schema)
      case _                 => buildNewPickler[T]()
    }

  /** Create a coproduct pickler (e.g. for an `enum` or `sealed trait`), where the value of the discriminator between child types is a read
    * of a field of the base type. The field, if not yet present, is added to each child schema.
    *
    * The picklers for the child types have to be provided explicitly with their value mappings in `mapping`.
    *
    * Note that if the discriminator value is some transformation of the child's type name (obtained using the implicit
    * [[PicklerConfiguration]]), the coproduct schema can be derived automatically or semi-automatically.
    *
    * @param discriminatorPickler
    *   The pickler that is used when adding the discriminator as a field to child picklers (if it's not yet added).
    */
  inline def oneOfUsingField[T: ClassTag, V](inline extractorFn: T => V, inline asStringFn: V => String)(
      mapping: (V, Pickler[_ <: T])*
  )(using m: Mirror.Of[T], c: PicklerConfiguration, discriminatorPickler: Pickler[V]): Pickler[T] =

    val paramMapping = mapping
    type ParamV = V
    val subtypeDiscriminator: SubtypeDiscriminator[T] = new CustomSubtypeDiscriminator[T] {
      type V = ParamV
      override lazy val fieldName = c.discriminator.getOrElse(SubtypeDiscriminator.DefaultFieldName)
      override def extractor = extractorFn
      override def asString = asStringFn
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
          case _: Mirror.SumOf[T] =>
            inline if (isEnumeration[T])
              error("oneOfUsingField cannot be used with enums. Try Pickler.derivedEnumeration instead.")
            else {
              given Schema[V] = discriminatorPickler.schema
              given Configuration = c.genericDerivationConfig
              val schema: Schema[T] = Schema.oneOfUsingField[T, V](extractorFn, asStringFn)(
                mapping.toList.map { case (v, p) =>
                  (v, p.schema)
                }: _*
              )
              lazy val childPicklers: Tuple.Map[m.MirroredElemTypes, Pickler] = summonChildPicklerInstances[T, m.MirroredElemTypes]
              picklerSum(schema, childPicklers, subtypeDiscriminator)
            }
        }
    }

  /** Creates a pickler for an enumeration, where the validator is derived using [[sttp.tapir.Validator.derivedEnumeration]]. This requires
    * that this is an `enum`, where all cases are parameterless, or that all subtypes of the sealed hierarchy `T` are `object`s.
    *
    * This method cannot be a `given`, as there's no way to constraint the type `T` to be an enum / sealed trait or class enumeration, so
    * that this would be invoked only when necessary.
    */
  inline def derivedEnumeration[T: ClassTag](using Mirror.Of[T]): CreateDerivedEnumerationPickler[T] =
    inline erasedValue[T] match
      case _: Null =>
        error("Unexpected non-enum Null passed to derivedEnumeration")
      case _: Nothing =>
        error("Unexpected non-enum Nothing passed to derivedEnumeration")
      case _: reflect.Enum =>
        new CreateDerivedEnumerationPickler(Validator.derivedEnumeration[T], SchemaAnnotations.derived[T])
      case _ =>
        error("Unexpected non-enum type passed to derivedEnumeration")

  inline given nonMirrorPickler[T](using PicklerConfiguration, NotGiven[Mirror.Of[T]]): Pickler[T] =
    summonFrom {
      // It turns out that summoning a Pickler can sometimes fall into this branch, even if we explicitly state that we wan't a NotGiven in the method signature
      case m: Mirror.Of[T] =>
        errorForType[T]("Failed to summon a Pickler[%s]. Try using Pickler.derived or importing sttp.tapir.json.pickler.generic.auto.*")
      case n: NotGiven[Mirror.Of[T]] =>
        Pickler(
          summonInline[Reader[T]],
          summonInline[Writer[T]],
          summonInline[Schema[T]]
        )
    }

  given picklerForOption[T: Pickler](using PicklerConfiguration): Pickler[Option[T]] =
    summon[Pickler[T]].asOption

  given picklerForIterable[T: Pickler, C[X] <: Iterable[X]](using PicklerConfiguration, Mirror.Of[T], Factory[T, C[T]]): Pickler[C[T]] =
    summon[Pickler[T]].asIterable[C]

  given picklerForEither[A, B](using pa: Pickler[A], pb: Pickler[B]): Pickler[Either[A, B]] =
    given Schema[A] = pa.schema
    given Schema[B] = pb.schema
    val newSchema = summon[Schema[Either[A, B]]]

    given TapirPickle.Writer[A] = pa.writer
    given TapirPickle.Writer[B] = pb.writer
    given TapirPickle.Reader[A] = pa.reader
    given TapirPickle.Reader[B] = pb.reader
    new Pickler[Either[A, B]](
      summon[Reader[Either[A, B]]],
      summon[Writer[Either[A, B]]],
      newSchema
    )

  given picklerForArray[T: Pickler: ClassTag]: Pickler[Array[T]] =
    summon[Pickler[T]].asArray

  inline given picklerForStringMap[V](using pv: Pickler[V]): Pickler[Map[String, V]] =
    given Schema[V] = pv.schema
    val newSchema = Schema.schemaForMap[V]
    given TapirPickle.Writer[V] = pv.writer
    given TapirPickle.Reader[V] = pv.reader
    new Pickler[Map[String, V]](
      summon[Reader[Map[String, V]]],
      summon[Writer[Map[String, V]]],
      newSchema
    )

  /** Create a pickler for a map with arbitrary keys. The pickler for the keys (`Pickler[K]`) should be string-like (that is, the schema
    * type should be [[sttp.tapir.SchemaType.SString]]), however this cannot be verified at compile-time and is not verified at run-time.
    *
    * The given `keyToString` conversion function is used during validation.
    *
    * If you'd like this pickler to be available as a given type of keys, create an custom implicit, e.g.:
    *
    * {{{
    * case class MyKey(value: String) extends AnyVal
    * given picklerForMyMap: Pickler[Map[MyKey, MyValue]] = Pickler.picklerForMap[MyKey, MyValue](_.value)
    * }}}
    */
  inline def picklerForMap[K, V](keyToString: K => String)(using pk: Pickler[K], pv: Pickler[V]): Pickler[Map[K, V]] =
    given Schema[V] = pv.schema
    val newSchema = Schema.schemaForMap[K, V](keyToString)
    given TapirPickle.Writer[K] = pk.writer
    given TapirPickle.Writer[V] = pv.writer
    given TapirPickle.Reader[K] = pk.reader
    given TapirPickle.Reader[V] = pv.reader
    new Pickler[Map[K, V]](
      summon[Reader[Map[K, V]]],
      summon[Writer[Map[K, V]]],
      newSchema
    )

  given Pickler[JBigDecimal] = new Pickler[JBigDecimal](
    summon[Reader[BigDecimal]].map(bd => bd.bigDecimal),
    summon[Writer[BigDecimal]].comap(jBd => BigDecimal(jBd)),
    summon[Schema[JBigDecimal]]
  )

  given Pickler[JBigInteger] = new Pickler[JBigInteger](
    summon[Reader[BigInt]].map(bi => bi.bigInteger),
    summon[Writer[BigInt]].comap(jBi => BigInt(jBi)),
    summon[Schema[JBigInteger]]
  )

  inline given picklerForAnyVal[T <: AnyVal]: Pickler[T] = ${ picklerForAnyValImpl[T]() }

  private inline def errorForType[T](inline template: String): Null = ${ errorForTypeImpl[T]('template) }

  private def errorForTypeImpl[T: Type](template: Expr[String])(using Quotes): Expr[Null] = {
    import quotes.reflect.*
    val templateStr = template.valueOrAbort
    val typeName = TypeRepr.of[T].show
    report.error(String.format(templateStr, typeName))
    '{ null }
  }

  private def picklerForAnyValImpl[T: Type]()(using quotes: Quotes): Expr[Pickler[T]] =
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]

    val isValueCaseClass =
      tpe.typeSymbol.isClassDef && tpe.classSymbol.get.flags.is(Flags.Case) && tpe.baseClasses.contains(Symbol.classSymbol("scala.AnyVal"))

    if (!isValueCaseClass) {
      '{ nonMirrorPickler[T] }
    } else {

      val field = tpe.typeSymbol.declaredFields.head
      val fieldTpe = tpe.memberType(field)
      fieldTpe.asType match
        case '[f] =>
          val basePickler = Expr.summon[Pickler[f]].getOrElse {
            report.errorAndAbort(
              s"Cannot summon Pickler for value class ${tpe.show}. Missing Pickler[${fieldTpe.show}] in implicit scope."
            )
          }
          '{
            val newSchema: Schema[T] = ${ basePickler }.schema.as[T]
            val writerF: Writer[f] = ${ basePickler }.writer
            val newWriter = writerF.comap[T](
              // writing object of type T means writing T.field
              ccObj => ${ Select.unique(('ccObj).asTerm, field.name).asExprOf[f] }
            )
            val readerF: Reader[f] = ${ basePickler }.reader
            val newReader =
              readerF.map[T](fieldObj => ${ Apply(Select.unique(New(Inferred(tpe)), "<init>"), List(('fieldObj).asTerm)).asExprOf[T] })

            new Pickler[T](
              newReader,
              newWriter,
              newSchema
            )
          }
    }

  private inline def fromExistingSchemaAndRw[T](schema: Schema[T])(using ClassTag[T], PicklerConfiguration, Mirror.Of[T]): Pickler[T] =
    val writer: Writer[T] = summonFrom {
      case foundW: Writer[T] =>
        foundW
      case foundW: Types#Writer[T] =>
        foundW.asInstanceOf[Writer[T]]
      case _ =>
        errorForType[T](
          "Found implicit Schema[%s] but couldn't find a uPickle Writer for this type. Either provide a Writer/ReadWriter, or remove the Schema from scope and let Pickler derive all on its own."
        )
    }
    val reader: Reader[T] = summonFrom {
      case foundR: Reader[T] =>
        foundR
      case foundR: Types#Reader[T] =>
        foundR.asInstanceOf[Reader[T]]
      case _ =>
        errorForType[T](
          "Found implicit Schema[%s] but couldn't find a uPickle Reader for this type. Either provide a Reader/ReadWriter, or remove the Schema from scope and let Pickler derive all on its own."
        )
    }
    Pickler(reader, writer, schema)

  private[pickler] inline def buildNewPickler[T: ClassTag]()(using m: Mirror.Of[T], config: PicklerConfiguration): Pickler[T] =
    inline m match
      case p: Mirror.ProductOf[T] =>
        // The lazy modifier is necessary for preventing infinite recursion in the derived instance for recursive types such as Lst
        lazy val childPicklers: Tuple.Map[m.MirroredElemTypes, Pickler] = summonChildPicklerInstances[T, m.MirroredElemTypes]
        picklerProduct(p, childPicklers)
      case sum: Mirror.SumOf[T] =>
        inline if (isEnumeration[T])
          new CreateDerivedEnumerationPickler(Validator.derivedEnumeration[T], SchemaAnnotations.derived[T]).defaultStringBased(using sum)
        else
          given Configuration = config.genericDerivationConfig
          val schema = Schema.derived[T]
          lazy val childPicklers: Tuple.Map[m.MirroredElemTypes, Pickler] = summonChildPicklerInstances[T, m.MirroredElemTypes]
          val discriminator: SubtypeDiscriminator[T] = DefaultSubtypeDiscriminator(config)
          picklerSum(schema, childPicklers, discriminator)

  private[pickler] inline def summonChildPicklerInstances[T: ClassTag, Fields <: Tuple](using
      m: Mirror.Of[T],
      c: PicklerConfiguration
  ): Tuple.Map[Fields, Pickler] =
    inline erasedValue[Fields] match {
      case _: (fieldType *: fieldTypesTail) =>
        val processedHead = deriveOrSummon[T, fieldType]
        val processedTail = summonChildPicklerInstances[T, fieldTypesTail]
        Tuple.fromArray((processedHead +: processedTail.toArray)).asInstanceOf[Tuple.Map[Fields, Pickler]]
      case _: EmptyTuple.type => EmptyTuple.asInstanceOf[Tuple.Map[Fields, Pickler]]
    }

  private inline def deriveOrSummon[T, FieldType](using PicklerConfiguration): Pickler[FieldType] =
    inline erasedValue[FieldType] match
      case _: T => deriveRec[T, FieldType]
      case _ =>
        summonFrom {
          case p: Pickler[FieldType] => p
          case _ =>
            errorForType[FieldType](
              "Failed to summon Pickler[%s]. Try using Pickler.derived or importing sttp.tapir.json.pickler.generic.auto.*"
            )
        }

  private inline def deriveRec[T, FieldType](using config: PicklerConfiguration): Pickler[FieldType] =
    inline erasedValue[T] match
      case _: FieldType => error("Infinite recursive derivation")
      case _            => Pickler.derived[FieldType](using summonInline[ClassTag[FieldType]], config, summonInline[Mirror.Of[FieldType]])

      // Extract child RWs from child picklers
      // create a new RW from scratch using children rw and fields of the product
      // use provided existing schema
      // use data from schema to customize the new schema
  private inline def picklerProduct[T: ClassTag, TFields <: Tuple](
      product: Mirror.ProductOf[T],
      childPicklers: => Tuple.Map[TFields, Pickler]
  )(using
      config: PicklerConfiguration
  ): Pickler[T] =
    lazy val derivedChildSchemas: Tuple.Map[TFields, Schema] =
      childPicklers.map([t] => (p: t) => p.asInstanceOf[Pickler[t]].schema).asInstanceOf[Tuple.Map[TFields, Schema]]
    val schema: Schema[T] = productSchema(derivedChildSchemas)
    // only now schema fields are enriched properly
    val enrichedChildSchemas = schema.schemaType.asInstanceOf[SProduct[T]].fields.map(_.schema)
    val childDefaults = enrichedChildSchemas.map(_.default.map(_._1))

    val newWriter: Writer[T] = macroProductW[T](
      schema,
      // https://users.scala-lang.org/t/tuple-mapping-traversal-in-scala-3/8829/2
      childPicklers.map([a] => (obj: a) => obj.asInstanceOf[Pickler[a]].writer).productIterator.toList,
      childDefaults,
      config
    )
    val newReader: Reader[T] = macroProductR[T](
      schema,
      childPicklers.map([a] => (obj: a) => obj.asInstanceOf[Pickler[a]].reader),
      childDefaults,
      product
    )
    Pickler[T](newReader, newWriter, schema)

  private inline def productSchema[T, TFields <: Tuple](childSchemas: Tuple.Map[TFields, Schema])(using
      config: PicklerConfiguration
  ): Schema[T] =
    SchemaDerivation.productSchema(config.genericDerivationConfig, childSchemas)

  private[tapir] inline def picklerSum[T: ClassTag, CP <: Tuple](
      schema: Schema[T],
      childPicklers: => CP,
      subtypeDiscriminator: SubtypeDiscriminator[T]
  )(using
      m: Mirror.Of[T],
      config: PicklerConfiguration
  ): Pickler[T] =
    val childPicklersList = childPicklers.productIterator.toList.asInstanceOf[List[Pickler[_ <: T]]]
    val newWriter: Writer[T] = TapirPickle.macroSumW[T](
      childPicklersList,
      subtypeDiscriminator
    )
    val newReader: Reader[T] =
      macroSumR[T](
        childPicklersList,
        subtypeDiscriminator
      )
    new Pickler[T](newReader, newWriter, schema)

/** A pickler combines the [[Schema]] of a type (which is used for documentation and validation of deserialized values), with a uPickle
  * encoder/decoder ([[ReadWriter]]). The pickler module can derive both the schema, and the uPickle readwriters in a single go, using a
  * common configuration API.
  *
  * An in-scope pickler instance is required by [[jsonBody]] (and its variants), but it can also be manually converted to a codec using
  * [[Pickler.toCodec]].
  */
@implicitNotFound(msg = """Could not summon a Pickler for type ${T}.
Picklers can be derived automatically by adding: `import sttp.tapir.json.pickler.generic.auto.*`, or manually using `Pickler.derived[T]`.
The latter is also useful for debugging derivation errors.
You can find more details in the docs: https://tapir.softwaremill.com/en/latest/endpoint/pickler.html.""")
case class Pickler[T](reader: Reader[T], writer: Writer[T], schema: Schema[T]):

  given TapirPickle.Writer[T] = this.writer
  given TapirPickle.Reader[T] = this.reader

  def toCodec: JsonCodec[T] =
    given schemaT: Schema[T] = schema

    Codec.json[T] { s =>
      Try(read[T](s)) match {
        case Success(v) => Value(v)
        case Failure(e) => Error(s, JsonDecodeException(errors = List.empty, e))
      }
    } { t => write(t) }

  def asOption: Pickler[Option[T]] =
    val newSchema = schema.asOption
    new Pickler[Option[T]](
      summon[Reader[Option[T]]],
      summon[Writer[Option[T]]],
      newSchema
    )

  def asIterable[C[X] <: Iterable[X]](using Factory[T, C[T]]): Pickler[C[T]] =
    val newSchema = schema.asIterable[C]
    new Pickler[C[T]](
      summon[Reader[C[T]]],
      summon[Writer[C[T]]],
      newSchema
    )

  def asArray(using ct: ClassTag[T]): Pickler[Array[T]] =
    val newSchema = schema.asArray
    new Pickler[Array[T]](
      summon[Reader[Array[T]]],
      summon[Writer[Array[T]]],
      newSchema
    )

given picklerToCodec[T](using p: Pickler[T]): JsonCodec[T] = p.toCodec
