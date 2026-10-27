package sttp.tapir.json.pickler.internal.compiletime

import scala.util.control.NoStackTrace

/** Errors raised during derivation.
  *
  * Modelling these as an ADT rather than passing strings around is what makes it possible to render one coherent, actionable message at the
  * end of a failed derivation instead of a pile of unrelated compiler errors.
  *
  * Every error site follows the `Log.error(err.message) >> MIO.fail(err)` pattern, so that the failure shows up both in the derivation log
  * and as the compile error.
  */
sealed trait PicklerDerivationError extends NoStackTrace with Product with Serializable {
  def message: String
  override def getMessage: String = message
}

object PicklerDerivationError {

  /** No derivation rule was applicable. `reasons` carries one entry per rule that declined, so the user can see why each one bowed out.
    */
  final case class UnsupportedType(typeName: String, reasons: List[String]) extends PicklerDerivationError {
    def message: String = {
      val summary =
        s"Cannot derive Pickler for $typeName: no implicit Schema was found and the type is not an Option, collection, Map, " +
          "singleton, case class or sealed hierarchy."
      if (reasons.isEmpty) summary else s"$summary\n${reasons.mkString("\n")}"
    }
  }

  final case class NoChildrenInSealedTrait(typeName: String) extends PicklerDerivationError {
    def message: String =
      s"Cannot derive Pickler for $typeName: it is a sealed hierarchy with no children"
  }

  /** A `Map` is written as a JSON object, whose keys are strings; any other key type needs a user-supplied conversion. */
  final case class NonStringMapKey(keyTypeName: String) extends PicklerDerivationError {
    def message: String =
      s"Cannot derive Pickler for a Map with non-String keys ($keyTypeName); use Pickler.picklerForMap with an explicit key encoder."
  }

  /** The `PicklerConfiguration` could not be evaluated during expansion. The codec half needs the configuration as a *value* (its name
    * transformations are invoked at compile time and the results handed to `JsonCodecMaker` as literals), so a configuration that is only
    * known at runtime cannot be supported.
    */
  final case class ConfigurationNotStatic(configExpr: String, reason: String) extends PicklerDerivationError {
    def message: String =
      s"""The PicklerConfiguration must be known at compile time, but `$configExpr` could not be evaluated: $reason
         |Define it as a `given`/`implicit val` with a right-hand side built from `PicklerConfiguration.default` and its
         |`with*` methods (or an `inline given`), either in the same compilation unit as the derivation or in an already
         |compiled module. Functions passed to `withToEncodedName` must themselves be evaluable, e.g. `_.toUpperCase`.""".stripMargin
  }

  /** A function from the `PicklerConfiguration` (`toEncodedName`, `toDiscriminatorValue`) was evaluated at compile time but threw. The
    * usual cause is a body Hearth's evaluator cannot interpret, e.g. one going through an implicit conversion such as `StringOps`.
    */
  final case class ConfigurationFunctionFailed(function: String, input: String, cause: Throwable) extends PicklerDerivationError {
    def message: String = {
      val root = Iterator.iterate(cause)(_.getCause).takeWhile(_ != null).toList.last
      // Hearth's evaluator reports its reasons through a private `ControlThrowable` case class with no message; being a
      // case class, it is still a `Product`, which is how the reasons are recovered.
      val detail = Option(root.getMessage)
        .orElse(root match {
          case p: Product => Some(p.productIterator.mkString("; "))
          case _          => None
        })
        .getOrElse(root.getClass.getSimpleName)
      s"""The PicklerConfiguration's `$function` could not be evaluated at compile time for `$input`: $detail
         |Names are computed during derivation, so the function has to be evaluable there: keep it to `java.lang.String` method calls
         |(e.g. `_.toUpperCase`, `n => n.toLowerCase.concat("_")`); string `+` and `StringOps` extensions (`.reverse`, `.capitalize`)
         |are not supported.""".stripMargin
    }
  }

  final case class NotASealedHierarchy(typeName: String, macroName: String) extends PicklerDerivationError {
    def message: String = s"$macroName can only be used with a sealed hierarchy or enum; $typeName is not one"
  }

  /** `derivedEnumeration` needs every case to be a singleton, because that is what a bare-string encoding can name. */
  final case class NotAnEnumeration(typeName: String, nonSingletons: List[String]) extends PicklerDerivationError {
    def message: String =
      s"""Pickler.derivedEnumeration can only be used with a sealed hierarchy or enum whose cases are all objects (or
         |parameterless enum cases); $typeName has cases with fields: ${nonSingletons.mkString(", ")}.
         |Use Pickler.derived[$typeName] or Pickler.oneOfUsingField instead.""".stripMargin
  }

  /** An all-singleton hierarchy is encoded as a bare string, which has no field to carry a discriminator. */
  final case class EnumerationInOneOfUsingField(typeName: String) extends PicklerDerivationError {
    def message: String =
      s"""Pickler.oneOfUsingField cannot be used with $typeName: all its cases are objects, so it is encoded as a bare
         |string with no field to hold the discriminator. Use Pickler.derivedEnumeration[$typeName].customStringBased(...)
         |to choose how each case is rendered.""".stripMargin
  }

  /** `oneOfUsingField` turns every mapping key into a jsoniter discriminator literal at compile time, so both the keys and `asString` have
    * to be evaluable during expansion.
    */
  final case class OneOfMappingNotStatic(detail: String) extends PicklerDerivationError {
    def message: String =
      s"""Pickler.oneOfUsingField needs its mapping keys and `asString` function to be known at compile time: $detail
         |Use literal keys (e.g. `200 -> picklerOk`) and a lambda over them (e.g. `code => s"code-$$code"`).""".stripMargin
  }

  /** Every leaf of the hierarchy has to be given a discriminator value; a leaf that is not would otherwise get a configuration-derived one
    * in the codec and none in the schema.
    */
  final case class IncompleteOneOfMapping(typeName: String, unmapped: List[String]) extends PicklerDerivationError {
    def message: String =
      s"""Pickler.oneOfUsingField for $typeName does not map every case of the hierarchy; missing: ${unmapped.mkString(", ")}.
         |Add a `value -> Pickler.derived[Case]` entry for each of them.""".stripMargin
  }

  final case class AmbiguousOneOfMapping(typeName: String, detail: String) extends PicklerDerivationError {
    def message: String = s"Pickler.oneOfUsingField for $typeName is ambiguous: $detail"
  }

  /** A `JsonValueCodec[X]` alone cannot be honoured for a structural `X`: the schema would still be derived from the class, documenting a
    * shape the codec no longer writes.
    */
  final case class CodecWithoutPickler(typeName: String, codecExpr: String) extends PicklerDerivationError {
    def message: String =
      s"""Found a JsonValueCodec[$typeName] in scope ($codecExpr), but no Pickler[$typeName].
         |A codec on its own would be used for the JSON while the Schema is still derived from the class, so the two
         |could disagree. Provide a `given Pickler[$typeName]` instead (it carries both), or remove the codec.""".stripMargin
  }

  /** jsoniter writes a tuple as a JSON array while tapir has no array-of-heterogeneous-elements schema (a case-class reading would document
    * an object with `_1`, `_2`, ... fields), so no agreeing pair exists.
    */
  final case class TupleNotSupported(typeName: String) extends PicklerDerivationError {
    def message: String =
      s"""Cannot derive Pickler for $typeName: tuples have no JSON schema in tapir (the codec would write an array,
         |the schema would document an object). Use a case class instead.""".stripMargin
  }

  final case class InvalidAnnotation(detail: String) extends PicklerDerivationError {
    def message: String = s"Cannot derive Pickler: $detail"
  }
}
