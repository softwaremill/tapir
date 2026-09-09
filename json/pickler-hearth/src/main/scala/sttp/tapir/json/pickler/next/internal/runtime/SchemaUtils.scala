package sttp.tapir.json.pickler.next.internal.runtime

import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType.{SArray, SCoproduct, SDiscriminator, SOpenProduct, SProduct, SProductField, SRef, SString, SchemaWithValue}
import sttp.tapir.json.pickler.next.PicklerConfiguration
import sttp.tapir.{FieldName, Schema, Validator}

/** Runtime constructors for [[Schema]] values, invoked by macro-generated code.
  *
  * Everything here must be public (generated code lives in user compilation units) and free of macro machinery. The split matters for
  * compile times as much as for readability: every bit of logic expressed here is logic the macro does not have to reify into a tree.
  *
  * The shapes produced here are pinned by `SchemaDerivationTest` in the incumbent `json/pickler` module; see
  * `doc/dev/schema-derivation-test-spec.md` for the assertion-by-assertion breakdown.
  */
object SchemaUtils {

  /** Typed empty list, so that cross-quotes can build field lists with `::` without inferring `Nothing`. */
  def emptyFieldList[T]: List[SProductField[T]] = Nil

  // -- Annotations ------------------------------------------------------------------------------------------------

  /** Fold tapir's `Schema.annotations.*` onto a schema, ignoring every other annotation silently.
    *
    * `@encodedName` is deliberately absent: on a type it is consumed while building the [[SName]], and on a field it is consumed by
    * [[productField]]. Applying it here as well would be a no-op at best.
    */
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def enrichSchema[T](schema: Schema[T], annotations: List[Any]): Schema[T] =
    annotations.foldLeft(schema) {
      case (s, ann: Schema.annotations.description)                => s.description(ann.text)
      case (s, ann: Schema.annotations.encodedExample)             => s.encodedExample(ann.example)
      case (s, ann: Schema.annotations.default[T @unchecked])      => s.default(ann.default, ann.encoded)
      case (s, ann: Schema.annotations.validate[T @unchecked])     => s.validate(ann.v)
      case (s, ann: Schema.annotations.validateEach[T @unchecked]) =>
        s.modifyUnsafe[T](Schema.ModifyCollectionElements)((_: Schema[T]).validate(ann.v))
      case (s, ann: Schema.annotations.format)    => s.format(ann.format)
      case (s, ann: Schema.annotations.title)     => s.title(ann.name)
      case (s, _: Schema.annotations.deprecated)  => s.deprecated(true)
      case (s, _: Schema.annotations.hidden)      => s.hidden(true)
      case (s, ann: Schema.annotations.customise) => ann.f(s).asInstanceOf[Schema[T]]
      case (s, _)                                 => s
    }

  /** The `@encodedName` carried by a type, if any. When present it *replaces* the whole derived [[SName]]. */
  def encodedNameFrom(annotations: List[Any]): Option[String] =
    annotations.collectFirst { case ann: Schema.annotations.encodedName => ann.name }

  // -- Products ---------------------------------------------------------------------------------------------------

  /** Build one product field.
    *
    * `index` is the position of the parameter in the primary constructor, which for a case class is also its `Product` element index.
    * `SProductField` compares by name and schema only, so the accessor does not affect the test assertions — but it does drive
    * `Schema.applyValidation`, so it has to be right.
    */
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def productField[T](
      scalaName: String,
      configEncodedName: String,
      fieldSchema: Schema[Any],
      index: Int,
      annotations: List[Any]
  ): SProductField[T] = {
    // An explicit `@encodedName` beats the configured member-name transformation.
    val encodedName = encodedNameFrom(annotations).getOrElse(configEncodedName)
    SProductField[T, Any](
      FieldName(scalaName, encodedName),
      enrichSchema(fieldSchema, annotations),
      t => Some(t.asInstanceOf[Product].productElement(index))
    )
  }

  def productSchema[T](name: SName, fields: List[SProductField[T]]): Schema[T] =
    Schema[T](SProduct[T](fields), Some(name))

  // -- Coproducts -------------------------------------------------------------------------------------------------

  /** Build a coproduct schema, injecting the discriminator field into every child product.
    *
    * Three details are pinned by the test suite and easy to get wrong:
    *   1. the discriminator field is appended **after** the declared fields, not prepended;
    *   2. its schema is a bare `Schema(SString())` carrying only the `EncodedDiscriminatorValue` attribute — adding a
    *      `Validator.enumeration` would change `Schema` equality and fail the assertions;
    *   3. the attribute goes on the **field's** schema, not on the child schema itself.
    *
    * Discriminator values are computed here rather than in the macro so that all eight `with*DiscriminatorValues` variants fall out of
    * `config.toDiscriminatorValue` for free.
    */
  def coproductSchema[T](
      name: SName,
      subtypes: List[Schema[Any]],
      config: PicklerConfiguration
  ): Schema[T] =
    coproductSchemaWithValues[T](
      name,
      subtypes.map(child => child -> child.name.map(config.toDiscriminatorValue).getOrElse("")),
      config.discriminator
    )

  /** As [[coproductSchema]], with the discriminator value of every child given explicitly — for `oneOfUsingField`, where the values come
    * from the user's function rather than from the configuration. The codec writes exactly these values into `discriminatorField`, so this
    * is what the schema has to document (core's `Schema.oneOfUsingField` would document a field named after the extractor instead, which
    * the JSON does not contain).
    */
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def coproductSchemaWithValues[T](
      name: SName,
      subtypesWithValues: List[(Schema[Any], String)],
      discriminatorField: String
  ): Schema[T] = {
    val withDiscriminator: List[(Schema[Any], String)] = subtypesWithValues.map { case (child, value) =>
      val enriched = child.schemaType match {
        case p: SProduct[Any @unchecked] if !p.fields.exists(_.name.encodedName == discriminatorField) =>
          val field = SProductField[Any, String](
            FieldName(discriminatorField, discriminatorField),
            Schema(SString[String]()).encodedDiscriminatorValue(value),
            _ => Some(value)
          )
          child.copy(schemaType = p.copy(fields = p.fields :+ field))
        case _ => child
      }
      enriched -> value
    }

    val mapping: Map[String, SRef[?]] = withDiscriminator.flatMap { case (child, value) =>
      child.name.map(childName => value -> SRef[Any](childName))
    }.toMap

    val enrichedSubtypes = withDiscriminator.map(_._1)

    Schema[T](
      SCoproduct[T](enrichedSubtypes, Some(SDiscriminator(FieldName(discriminatorField, discriminatorField), mapping))) { (value: T) =>
        val className = value.getClass.getName
        enrichedSubtypes.collectFirst {
          case s if s.name.exists(n => className == n.fullName || className == n.fullName + "$") =>
            SchemaWithValue(s.asInstanceOf[Schema[Any]], value)
        }
      },
      Some(name)
    )
  }

  /** Reference to a schema being derived further up the stack — how recursion terminates. */
  def refSchema[T](name: SName): Schema[T] = Schema[T](SRef[T](name))

  // -- Wrappers ---------------------------------------------------------------------------------------------------

  // These return `Schema[Any]` because `Schema` is invariant and the macro needs to build homogeneous lists; the
  // generated code casts back at the use site.

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def optionSchema[E](elementSchema: Schema[E]): Schema[Any] =
    elementSchema.asOption.asInstanceOf[Schema[Any]]

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def collectionSchema[E](elementSchema: Schema[E]): Schema[Any] =
    Schema[Any](SArray[Any, E](elementSchema)(_.asInstanceOf[Iterable[E]]), isOptional = true)

  /** Mirrors what `Schema.schemaForMap` generates, but with our own derived value schema.
    *
    * `typeParameters` is computed by the macro and follows tapir's convention: the key type is omitted when it is `String`, and nested type
    * arguments are flattened.
    */
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def mapSchema[V](valueSchema: Schema[V], typeParameters: List[String]): Schema[Any] =
    Schema[Any](
      SOpenProduct[Any, V](Nil, valueSchema)(_.asInstanceOf[Map[String, V]]),
      Some(SName("Map", typeParameters))
    )

  /** tapir core's own `Either` schema — an untagged coproduct of the two sides — over our derived side schemas. The codec
    * (`CodecCombinators.either`) writes the bare side value, which is what this schema documents.
    */
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def eitherSchema[L, R](left: Schema[L], right: Schema[R]): Schema[Any] =
    Schema.schemaForEither(left, right).asInstanceOf[Schema[Any]]

  /** A bare `SString` schema, for a type that tapir core has no schema for but that the codec writes as a string. `Char` is the only such
    * type today.
    */
  def stringLikeSchema[T]: Schema[T] = Schema(SString[T]())

  /** A string-valued schema whose validator enumerates the singleton values of an enum-like hierarchy. The validator carries the name too,
    * as core's `Validator.derivedEnumeration` does: the OpenAPI interpreter uses it to emit a named component for the enumeration.
    */
  def stringEnumSchema[T](name: SName, values: List[T], encodedNames: List[String]): Schema[T] = {
    val encoded = values.zip(encodedNames).toMap
    Schema.string[T].name(name).copy(validator = Validator.enumeration(values, (v: T) => encoded.get(v), Some(name)))
  }

  // -- Names ------------------------------------------------------------------------------------------------------

  /** Build an [[SName]] from a name computed by tapir core's own `SNameMacros`, plus type arguments recovered from the printed form of the
    * type.
    *
    * The split exists because the two halves are best obtained from different places. `SNameMacros.typeFullName` walks the *symbol* owner
    * chain, which is the only way to get a genuinely qualified name for a class nested in another class (whose type prints as the
    * path-dependent `Outer.this.Inner`), and it applies core's conventions for module suffixes and synthetic `<local ...>` owners. The
    * printed form, meanwhile, is the only place the applied type arguments survive.
    */
  def sName(fullName: String, printedType: String): SName =
    SName(fullName, parseSName(printedType).typeParameterShortNames)

  /** Turn a fully-qualified, Scala-syntax type name into an [[SName]].
    *
    * `SName.typeParameterShortNames` is a misnomer inherited from tapir: it holds *fully-qualified* names, and nested arguments are
    * flattened into one list. `Foo[Bar[Baz], Qux]` therefore becomes `SName("Foo", List("Bar", "Baz", "Qux"))` with each entry fully
    * qualified — matching `Schema.renameWithTypeParameter`.
    */
  def parseSName(fullTypeName: String): SName = {
    val bracket = fullTypeName.indexOf('[')
    if (bracket < 0) SName(fullTypeName)
    else
      SName(
        fullTypeName.substring(0, bracket),
        splitTopLevel(fullTypeName.substring(bracket + 1, fullTypeName.length - 1)).flatMap(flattenTypeName)
      )
  }

  /** `List[Int]` => `List("List", "Int")`, fully qualified, in tapir's flattened order. */
  def flattenTypeName(typeName: String): List[String] = {
    val bracket = typeName.indexOf('[')
    if (bracket < 0) List(typeName)
    else
      typeName.substring(0, bracket) ::
        splitTopLevel(typeName.substring(bracket + 1, typeName.length - 1)).flatMap(flattenTypeName)
  }

  /** Split on commas that are not nested inside brackets. */
  private def splitTopLevel(s: String): List[String] = {
    val result = List.newBuilder[String]
    var depth = 0
    var start = 0
    var i = 0
    while (i < s.length) {
      s.charAt(i) match {
        case '['               => depth += 1
        case ']'               => depth -= 1
        case ',' if depth == 0 =>
          result += s.substring(start, i).trim
          start = i + 1
        case _ => ()
      }
      i += 1
    }
    if (start < s.length) result += s.substring(start).trim
    result.result()
  }
}
