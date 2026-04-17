package sttp.tapir.json.pickler

import sttp.tapir.internal.EnumerationMacros.*
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.SchemaType.SProduct
import sttp.tapir.{Codec, Schema, SchemaAnnotations, Validator}

import scala.collection.Factory
import scala.compiletime.*
import scala.deriving.Mirror
import scala.quoted.*
import scala.reflect.ClassTag
import scala.util.{Failure, NotGiven, Success, Try}
import java.math.{BigDecimal as JBigDecimal, BigInteger as JBigInteger}

import scala.annotation.implicitNotFound
import sttp.tapir.generic.Configuration

object Pickler:

  /** Derive a [[Pickler]] instance for the given type, at compile-time. Depending on the derivation mode (auto / semi-auto), picklers for
    * referenced types (e.g. via a field, enum case or subtype) will either be derived automatically, or will need to be provided manually.
    *
    * This method can either be used explicitly, in the definition of a `given`, or indirectly by adding a `... derives Pickler` modifier to
    * a datatype definition.
    *
    * The in-scope [[PicklerConfiguration]] instance is used to customise field names and other behavior.
    */
  inline def derived[T: ClassTag](using PicklerConfiguration): Pickler[T] =
    summonFrom {
      case schema: Schema[T] => fromExistingSchemaAndRw[T](schema)
      case m: Mirror.Of[T]   => buildNewPickler[T]()
      case _ => errorForType[T]("Cannot derive Pickler[%s], you need to provide both Schema and uPickle ReadWriter for this type.")
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
      override lazy val fieldName = c.discriminator
      override def extractor = extractorFn
      override def asString = asStringFn
      override lazy val mapping = paramMapping
    }
    summonFrom {
      case schema: Schema[T] => fromExistingSchemaAndRw[T](schema)
      case _                 =>
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
    * that this is an `enum`, where all cases are parameterless, or that all subtypes of the sealed hierarchy `T` are `object` s.
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
      case n: NotGiven[Mirror.Of[T]] =>
        Pickler(
          new TapirPickle[T] {
            override lazy val reader = summonFrom {
              case r: Reader[T] => r
              case _            =>
                errorForType[T](
                  "Use Pickler.derive[%s] instead of nonMirrorPickler. This method has to be in scope to resolve predefined picklers."
                )
            }
            override lazy val writer = summonFrom {
              case w: Writer[T] => w
              case _            =>
                errorForType[T](
                  "Use Pickler.derive[%s] instead of nonMirrorPickler. This method has to be in scope to resolve predefined picklers."
                )
            }
          },
          summonInline[Schema[T]]
        )
      // It turns out that summoning a Pickler can sometimes fall into this branch, even if we explicitly state that we want a NotGiven in the method signature
      case m: Mirror.Of[T] =>
        errorForType[T](
          "Found unexpected Mirror. Failed to summon a Pickler[%s]. Please report it as an issue at https://github.com/softwaremill/tapir/issues. To avoid this issue, try using Pickler.derived or importing sttp.tapir.json.pickler.generic.auto.*"
        )
    }

  given picklerForOption[T: Pickler](using PicklerConfiguration, Mirror.Of[T]): Pickler[Option[T]] =
    summon[Pickler[T]].asOption

  given picklerForIterable[T: Pickler, C[X] <: Iterable[X]](using PicklerConfiguration, Mirror.Of[T], Factory[T, C[T]]): Pickler[C[T]] =
    summon[Pickler[T]].asIterable[C]

  given picklerForEither[A, B](using pa: Pickler[A], pb: Pickler[B]): Pickler[Either[A, B]] =
    given Schema[A] = pa.schema
    given Schema[B] = pb.schema
    val newSchema = summon[Schema[Either[A, B]]]

    new Pickler[Either[A, B]](
      new TapirPickle[Either[A, B]] {
        given Reader[A] = pa.innerUpickle.reader.asInstanceOf[Reader[A]]
        given Writer[A] = pa.innerUpickle.writer.asInstanceOf[Writer[A]]
        given Reader[B] = pb.innerUpickle.reader.asInstanceOf[Reader[B]]
        given Writer[B] = pb.innerUpickle.writer.asInstanceOf[Writer[B]]
        override lazy val writer = summon[Writer[Either[A, B]]]
        override lazy val reader = summon[Reader[Either[A, B]]]
      },
      newSchema
    )

  given picklerForArray[T: Pickler: ClassTag]: Pickler[Array[T]] =
    summon[Pickler[T]].asArray

  inline given picklerForStringMap[V](using pv: Pickler[V]): Pickler[Map[String, V]] =
    given Schema[V] = pv.schema
    val newSchema = Schema.schemaForMap[V]
    new Pickler[Map[String, V]](
      new TapirPickle[Map[String, V]] {
        given Reader[V] = pv.innerUpickle.reader.asInstanceOf[Reader[V]]
        given Writer[V] = pv.innerUpickle.writer.asInstanceOf[Writer[V]]
        override lazy val writer = summon[Writer[Map[String, V]]]
        override lazy val reader = summon[Reader[Map[String, V]]]
      },
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
    new Pickler[Map[K, V]](
      new TapirPickle[Map[K, V]] {
        given Reader[K] = pk.innerUpickle.reader.asInstanceOf[Reader[K]]
        given Writer[K] = pk.innerUpickle.writer.asInstanceOf[Writer[K]]
        given Reader[V] = pv.innerUpickle.reader.asInstanceOf[Reader[V]]
        given Writer[V] = pv.innerUpickle.writer.asInstanceOf[Writer[V]]
        override lazy val writer = summon[Writer[Map[K, V]]]
        override lazy val reader = summon[Reader[Map[K, V]]]
      },
      newSchema
    )

  given Pickler[JBigDecimal] = new Pickler[JBigDecimal](
    new TapirPickle[JBigDecimal] {
      override lazy val writer = summon[Writer[BigDecimal]].comap(jBd => BigDecimal(jBd))
      override lazy val reader = summon[Reader[BigDecimal]].map(bd => bd.bigDecimal)
    },
    summon[Schema[JBigDecimal]]
  )

  given Pickler[JBigInteger] = new Pickler[JBigInteger](
    new TapirPickle[JBigInteger] {
      override lazy val writer = summon[Writer[BigInt]].comap(jBi => BigInt(jBi))
      override lazy val reader = summon[Reader[BigInt]].map(bi => bi.bigInteger)
    },
    summon[Schema[JBigInteger]]
  )

  inline given picklerForAnyVal[T <: AnyVal]: Pickler[T] = ${ picklerForAnyValImpl[T] }

  //

  private inline def errorForType[T](inline template: String): Null = ${ errorForTypeImpl[T]('template) }

  private def errorForTypeImpl[T: Type](template: Expr[String])(using Quotes): Expr[Null] = {
    import quotes.reflect.*
    val templateStr = template.valueOrAbort
    val typeName = TypeRepr.of[T].show
    report.error(String.format(templateStr, typeName))
    '{ null }
  }

  private def picklerForAnyValImpl[T: Type](using quotes: Quotes): Expr[Pickler[T]] =
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
            new Pickler[T](
              new TapirPickle[T] {
                override lazy val writer = summonInline[Writer[f]].comap[T](
                  // writing object of type T means writing T.field
                  ccObj => ${ Select.unique(('ccObj).asTerm, field.name).asExprOf[f] }
                )
                // a reader of type f (field) will read it and wrap into value object using the consutructor of T
                override lazy val reader = summonInline[Reader[f]]
                  .map[T](fieldObj => ${ Apply(Select.unique(New(Inferred(tpe)), "<init>"), List(('fieldObj).asTerm)).asExprOf[T] })
              },
              newSchema
            )
          }
    }

  private inline def fromExistingSchemaAndRw[T](schema: Schema[T])(using ClassTag[T], PicklerConfiguration): Pickler[T] =
    Pickler(
      new TapirPickle[T] {
        override lazy val reader: Reader[T] = summonFrom {
          case foundR: _root_.upickle.core.Types#Reader[T] =>
            foundR.asInstanceOf[Reader[T]]
          case _ =>
            errorForType[T](
              "Found implicit Schema[%s] but couldn't find a uPickle Reader for this type. Either provide a Reader/ReadWriter, or remove the Schema from scope and let Pickler derive all on its own."
            )
            null
        }
        override lazy val writer: Writer[T] = summonFrom {
          case foundW: _root_.upickle.core.Types#Writer[T] =>
            foundW.asInstanceOf[Writer[T]]
          case _ =>
            errorForType[T](
              "Found implicit Schema[%s] but couldn't find a uPickle Writer for this type. Either provide a Writer/ReadWriter, or remove the Schema from scope and let Pickler derive all on its own."
            )
            null
        }
      },
      schema
    )

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
      case _    =>
        summonFrom {
          case p: Pickler[FieldType] => p
          case _                     =>
            errorForType[FieldType](
              "Failed to summon Pickler[%s]. Try using Pickler.derived or importing sttp.tapir.json.pickler.generic.auto.*"
            )
        }

  private inline def deriveRec[T, FieldType](using config: PicklerConfiguration): Pickler[FieldType] =
    inline erasedValue[T] match
      case _: FieldType => error("Infinite recursive derivation")
      case _            => Pickler.derived[FieldType](using summonInline[ClassTag[FieldType]], config)

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

    val tapirPickle = new TapirPickle[T] {
      override lazy val writer: Writer[T] =
        macroProductW[T](
          schema,
          childPicklers.map([a] => (obj: a) => obj.asInstanceOf[Pickler[a]].innerUpickle.writer).productIterator.toList,
          childDefaults,
          config
        )
      override lazy val reader: Reader[T] =
        macroProductR[T](
          schema,
          childPicklers.map([a] => (obj: a) => obj.asInstanceOf[Pickler[a]].innerUpickle.reader),
          childDefaults,
          product,
          config
        )
    }
    Pickler[T](tapirPickle, schema)

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
    val tapirPickle = new TapirPickle[T] {
      override lazy val writer: Writer[T] =
        macroSumW[T](
          childPicklersList,
          subtypeDiscriminator
        )
      override lazy val reader: Reader[T] =
        macroSumR[T](
          childPicklersList,
          subtypeDiscriminator
        )
    }
    new Pickler[T](tapirPickle, schema)

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
case class Pickler[T](innerUpickle: TapirPickle[T], schema: Schema[T]):

  def toCodec: JsonCodec[T] =
    import innerUpickle._
    given innerUpickle.Reader[T] = innerUpickle.reader
    given innerUpickle.Writer[T] = innerUpickle.writer
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
      new TapirPickle[Option[T]] {
        given Reader[T] = innerUpickle.reader.asInstanceOf[Reader[T]]
        given Writer[T] = innerUpickle.writer.asInstanceOf[Writer[T]]
        override lazy val writer = summon[Writer[Option[T]]]
        override lazy val reader = summon[Reader[Option[T]]]
      },
      newSchema
    )

  def asIterable[C[X] <: Iterable[X]](using Factory[T, C[T]]): Pickler[C[T]] =
    val newSchema = schema.asIterable[C]
    new Pickler[C[T]](
      new TapirPickle[C[T]] {
        given Reader[T] = innerUpickle.reader.asInstanceOf[Reader[T]]
        given Writer[T] = innerUpickle.writer.asInstanceOf[Writer[T]]
        override lazy val writer = summon[Writer[C[T]]]
        override lazy val reader = summon[Reader[C[T]]]
      },
      newSchema
    )

  def asArray(using ct: ClassTag[T]): Pickler[Array[T]] =
    val newSchema = schema.asArray
    new Pickler[Array[T]](
      new TapirPickle[Array[T]] {
        given Reader[T] = innerUpickle.reader.asInstanceOf[Reader[T]]
        given Writer[T] = innerUpickle.writer.asInstanceOf[Writer[T]]
        override lazy val writer = summon[Writer[Array[T]]]
        override lazy val reader = summon[Reader[Array[T]]]
      },
      newSchema
    )

given picklerToCodec[T](using p: Pickler[T]): JsonCodec[T] = p.toCodec
