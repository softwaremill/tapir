package sttp.tapir.json.pickler.internal.compiletime

import hearth.MacroCommons
import hearth.std.*

/** The one place where a type is taken apart.
  *
  * Both halves of the derivation — the schema rules in [[SchemaDerivation]] and the codec walk in [[CodecDerivation]] — consume the
  * [[Shape]] computed here, so they cannot disagree about *what* a type is: whether `String` is a scalar or a collection of `Char`, whether
  * an `Either` is a coproduct, whether a hierarchy is an enumeration (bare strings) or a coproduct (discriminated objects), and so on. What
  * each half *does* with a shape is its own business; the classification is not.
  *
  * The order of the cases in [[classify]] matters and is deliberate:
  *   - the scalar-likes come first because the structural extractors would happily match them (`String` is an `Iterable[Char]`);
  *   - `AnyVal` wrappers before `Option`/collections, since a value class may wrap one;
  *   - `Either` before the sealed-hierarchy check, which would otherwise claim it as `Left | Right`;
  *   - `Map` before `Collection`, since a `Map` is an `Iterable` of pairs;
  *   - singletons before case classes (a `case object` parses as both), tuples before case classes (likewise).
  */
trait TypeShape { this: MacroCommons & StdExtensions =>

  /** Centralised `Type.of` instances — see the note on `PicklerMacrosImpl.PTypes` for why these are not `implicit val`s at their use sites.
    */
  private object ShapeTypes {
    lazy val StringT: Type[String] = Type.of[String]
    lazy val CharT: Type[Char] = Type.of[Char]
    lazy val AnyValT: Type[AnyVal] = Type.of[AnyVal]
    lazy val JBigDecimalT: Type[java.math.BigDecimal] = Type.of[java.math.BigDecimal]
    lazy val JBigIntegerT: Type[java.math.BigInteger] = Type.of[java.math.BigInteger]
    lazy val EitherCtor: Type.Ctor2[Either] = Type.Ctor2.of[Either]
  }

  sealed trait Shape[A]
  object Shape {

    /** `String` or `Array[Byte]`: tapir has a built-in `Schema` and jsoniter a built-in codec, but the structural extractors would take
      * both apart as collections.
      */
    final case class BuiltInScalar[A]() extends Shape[A]

    /** `Char`: jsoniter writes it as a one-character string; tapir core declares no `Schema[Char]`, so the schema side supplies one. */
    final case class CharScalar[A]() extends Shape[A]

    /** `java.math.BigDecimal` / `java.math.BigInteger`: tapir has a `Schema`, `JsonCodecMaker` has no codec (see `LeafCodecs`). */
    final case class JavaBigDecimal[A]() extends Shape[A]
    final case class JavaBigInteger[A]() extends Shape[A]

    /** An `AnyVal` wrapper: documented and written as its inner type. Restricted to `AnyVal` because Hearth's `IsValueType` also matches
      * opaque types and Java boxes, which jsoniter does not unwrap.
      */
    final case class ValueClass[A](inner: ??) extends Shape[A]

    final case class OptionOf[A](element: ??) extends Shape[A]

    /** `Option[Option[X]]`, flattened to a nullable `X`. `inner` is `Option[X]`, `element` is `X`. */
    final case class NestedOption[A](inner: ??, element: ??) extends Shape[A]

    /** Untagged: written as the bare side value, documented as a coproduct without discriminator. */
    final case class EitherOf[A](left: ??, right: ??) extends Shape[A]

    /** A `Map` with `String` keys: a JSON object. */
    final case class StringMap[A](value: ??) extends Shape[A]

    /** A `Map` with any other key type: not derivable, the user has to supply `Pickler.picklerForMap`. */
    final case class NonStringMap[A](key: ??, value: ??) extends Shape[A]

    final case class Collection[A](element: ??) extends Shape[A]

    /** Not supported: jsoniter writes a tuple as an array, tapir would document an object. */
    final case class Tuple[A]() extends Shape[A]

    /** A `case object` or parameterless enum case, on its own (not as a member of an enumeration). */
    final case class Singleton[A]() extends Shape[A]

    final case class Product[A](cc: CaseClass[A], params: List[(String, Parameter)]) extends Shape[A]

    /** A sealed hierarchy whose leaves are all singletons: encoded as bare strings, documented as a string enumeration. Never empty. */
    final case class Enumeration[A](e: Enum[A], leaves: List[(String, ??<:[A])]) extends Shape[A]

    /** A sealed hierarchy with at least one non-singleton leaf (or no leaves at all, which is an error downstream): discriminated objects.
      */
    final case class Coproduct[A](e: Enum[A], leaves: List[(String, ??<:[A])]) extends Shape[A]

    /** Anything else: primitives, `java.time`, `UUID`, ... Resolved through an implicit `Schema` on one side and jsoniter's built-in codecs
      * on the other.
      */
    final case class Opaque[A]() extends Shape[A]
  }

  def classify[A: Type]: Shape[A] = {
    implicit val StringT: Type[String] = ShapeTypes.StringT
    implicit val CharT: Type[Char] = ShapeTypes.CharT
    implicit val AnyValT: Type[AnyVal] = ShapeTypes.AnyValT
    implicit val JBigDecimalT: Type[java.math.BigDecimal] = ShapeTypes.JBigDecimalT
    implicit val JBigIntegerT: Type[java.math.BigInteger] = ShapeTypes.JBigIntegerT
    val EitherCtor = ShapeTypes.EitherCtor

    if (Type[A] <:< Type[String] || Type[A].plainPrint == "scala.Array[scala.Byte]") Shape.BuiltInScalar()
    else if (Type[A] =:= Type[Char]) Shape.CharScalar()
    else if (Type[A] =:= Type[java.math.BigDecimal]) Shape.JavaBigDecimal()
    else if (Type[A] =:= Type[java.math.BigInteger]) Shape.JavaBigInteger()
    else
      Type[A] match {
        case IsValueType(isValueType) if Type[A] <:< Type[AnyVal] =>
          import isValueType.Underlying as Inner
          Shape.ValueClass(Type[Inner].as_??)
        case IsOption(isOption) =>
          import isOption.Underlying as Element
          Type[Element] match {
            case IsOption(isInner) =>
              import isInner.Underlying as Innermost
              Shape.NestedOption(Type[Element].as_??, Type[Innermost].as_??)
            case _ => Shape.OptionOf(Type[Element].as_??)
          }
        case EitherCtor(left, right) => Shape.EitherOf(left, right)
        case IsMap(isMap)            =>
          import isMap.Underlying as Pair
          mapShape[A, Pair](isMap.value)
        case IsCollection(isCollection) =>
          import isCollection.Underlying as Element
          Shape.Collection(Type[Element].as_??)
        case _ if Type[A].isTuple => Shape.Tuple()
        case _ if isSingleton[A]  => Shape.Singleton()
        case _                    =>
          CaseClass.parse[A].toEither match {
            case Right(cc) => Shape.Product(cc, cc.primaryConstructor.totalParameters.flatten.toList)
            case Left(_)   =>
              Enum.parse[A].toEither match {
                case Right(e) =>
                  val leaves = leavesOf(e)
                  if (leaves.nonEmpty && allSingletons(leaves)) Shape.Enumeration(e, leaves) else Shape.Coproduct(e, leaves)
                case Left(_) => Shape.Opaque()
              }
          }
      }
  }

  private def mapShape[A: Type, Pair: Type](isMap: IsMapOf[A, Pair]): Shape[A] = {
    import isMap.{Key, Value}
    implicit val StringT: Type[String] = ShapeTypes.StringT
    if (Key <:< Type[String]) Shape.StringMap(Type[Value].as_??) else Shape.NonStringMap(Key.as_??, Type[Value].as_??)
  }

  /** The **leaves** of a sealed hierarchy: an intermediate sealed trait contributes its own children, not itself, so `Pet -> Rodent ->
    * Hamster` yields `Hamster` as a direct subtype. Falls back to the direct children when the hierarchy cannot be enumerated exhaustively.
    */
  def leavesOf[A](e: Enum[A]): List[(String, ??<:[A])] =
    e.exhaustiveChildren.map(_.toList).getOrElse(e.directChildren.toList)

  def isSingleton[A: Type]: Boolean = SingletonValue.parse[A].toEither.isRight

  def allSingletons[A](leaves: List[(String, ??<:[A])]): Boolean = leaves.forall { case (_, leaf) =>
    import leaf.Underlying as Leaf
    isSingleton[Leaf]
  }

  /** The leaves that are *not* singletons, by name — for error messages. */
  def nonSingletonLeaves[A](leaves: List[(String, ??<:[A])]): List[String] = leaves.collect {
    case (_, leaf) if {
          import leaf.Underlying as Leaf
          !isSingleton[Leaf]
        } =>
      leaf.Underlying.plainPrint
  }

  /** The children the codec walk has to visit for a shape: everything the shape's codec is built from. */
  def childrenOf[A](shape: Shape[A]): List[??] = shape match {
    case Shape.ValueClass(inner)      => List(inner)
    case Shape.OptionOf(element)      => List(element)
    case Shape.NestedOption(inner, _) => List(inner)
    case Shape.EitherOf(left, right)  => List(left, right)
    case Shape.StringMap(value)       => List(value)
    case Shape.NonStringMap(_, value) => List(value)
    case Shape.Collection(element)    => List(element)
    case Shape.Product(_, params)     => params.map { case (_, param) => param.tpe }
    case Shape.Enumeration(_, leaves) => leafTypes(leaves)
    case Shape.Coproduct(_, leaves)   => leafTypes(leaves)
    case _                            => Nil
  }

  private def leafTypes[A](leaves: List[(String, ??<:[A])]): List[??] = leaves.map { case (_, leaf) =>
    import leaf.Underlying as Leaf
    Type[Leaf].as_??
  }
}
