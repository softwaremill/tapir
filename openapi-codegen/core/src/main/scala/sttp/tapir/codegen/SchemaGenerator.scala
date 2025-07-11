package sttp.tapir.codegen

import sttp.tapir.codegen.RootGenerator.indent
import sttp.tapir.codegen.JsonSerdeLib.JsonSerdeLib
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  Discriminator,
  OpenapiSchemaAllOf,
  OpenapiSchemaAny,
  OpenapiSchemaAnyOf,
  OpenapiSchemaArray,
  OpenapiSchemaByte,
  OpenapiSchemaConstantString,
  OpenapiSchemaEnum,
  OpenapiSchemaField,
  OpenapiSchemaMap,
  OpenapiSchemaNot,
  OpenapiSchemaObject,
  OpenapiSchemaOneOf,
  OpenapiSchemaRef,
  OpenapiSchemaSimpleType
}

import scala.collection.mutable

object SchemaGenerator {
  def decl(isRecursive: Boolean): String = if (isRecursive) "def" else "lazy val"

  def generateSchemas(
      doc: OpenapiDocument,
      allSchemas: Map[String, OpenapiSchemaType],
      fullModelPath: String,
      jsonSerdeLib: JsonSerdeLib,
      maxSchemasPerFile: Int,
      schemasContainAny: Boolean,
      targetScala3: Boolean
  ): Seq[String] = {
    val maybeAnySchema: Option[(String, Boolean => String)] =
      if (!schemasContainAny) None
      else if (jsonSerdeLib == JsonSerdeLib.Circe || jsonSerdeLib == JsonSerdeLib.Jsoniter)
        Some(
          "anyTapirSchema" -> (_ =>
            "implicit lazy val anyTapirSchema: sttp.tapir.Schema[io.circe.Json] = sttp.tapir.Schema.any[io.circe.Json]"
          )
        )
      else throw new NotImplementedError("any not implemented for json libs other than circe and jsoniter")
    val extraSchemaRefs: Seq[Seq[(String, OpenapiSchemaType)]] = Seq(
      Seq("byteStringSchema" -> OpenapiSchemaByte(false)),
      maybeAnySchema.map { case (_, _) => "anyTapirSchema" -> OpenapiSchemaAny(false) }.toSeq
    )
    val openApiSchemasWithTapirSchemas: Map[String, Boolean => String] =
      doc.components
        .map(_.schemas.toSeq.flatMap {
          case (name, _: OpenapiSchemaEnum) =>
            Some(
              name -> ((_: Boolean) =>
                s"implicit lazy val ${RootGenerator.uncapitalise(name)}TapirSchema: sttp.tapir.Schema[$name] = sttp.tapir.Schema.derived"
              )
            )
          case (name, obj: OpenapiSchemaObject)   => Some(name -> (schemaForObject(name, _, obj)))
          case (name, schema: OpenapiSchemaMap)   => Some(name -> (schemaForMapOrArray(name, _, schema.items)))
          case (name, schema: OpenapiSchemaArray) => Some(name -> (schemaForMapOrArray(name, _, schema.items)))
          case (_, _: OpenapiSchemaAny)           => None
          case (name, schema: OpenapiSchemaOneOf) =>
            Some(name -> (genADTSchema(name, schema, _, if (fullModelPath.isEmpty) None else Some(fullModelPath))))
          case (n, x) => throw new NotImplementedError(s"Only objects, enums, maps, arrays and oneOf supported! (for $n found ${x})")
        })
        .toSeq
        .flatMap(maybeAnySchema.toSeq ++ _)
        .toMap + ("byteStringSchema" -> (_ =>
        "implicit lazy val byteStringSchema: sttp.tapir.Schema[ByteString] = sttp.tapir.Schema.schemaForByteArray.map(ba => Some(toByteString(ba)))(bs => bs)"
      ))

    // The algorithm here is to avoid mutually references between objects. It goes like this:
    // 1) Find all 'rings' -- that is, sets of mutually-recursive object references that will need to be defined in the same object
    // (e.g. the schemas for `case class A(maybeB: Option[B])` and `case class B(maybeA: Option[A])` would need to be defined together)
    val groupedByRing = constructRings(allSchemas)
    // 2) Order the definitions, such that objects appear before any places they're referenced
    val orderedLayers = orderLayers(groupedByRing)
    // 3) Group the definitions into at most `maxSchemasPerFile`, whilst avoiding splitting groups across files
    // We also return an `isRecursive` boolean here, so that we can appropriately use 'def' instead of 'lazy val' for
    // defns that would otherwise break in scala 3. Since this will _not_ break in scala 2, we return true _only_ when
    // the target is scala 3
    val foldedLayers = foldLayers(maxSchemasPerFile, targetScala3)(extraSchemaRefs ++ orderedLayers)
    // Our output will now only need to import the 'earlier' files into the 'later' files, and _not_ vice verse
    foldedLayers.map(ring => ring.map(r => openApiSchemasWithTapirSchemas(r._1)(r._2)).mkString("\n"))
  }
  // Group files into chunks of size < maxLayerSize
  // The boolean in the return signature indicates whether the schema is part of a recursive definition or not.
  // This is required since for scala 3, these schemas must be declared with `def` rather than `lazy val`
  private def foldLayers(
      maxLayerSize: Int,
      isScala3: Boolean
  )(layers: Seq[Seq[(String, OpenapiSchemaType)]]): Seq[Seq[(String, Boolean, OpenapiSchemaType)]] = {
    def withRecursiveBoolean(s: Seq[(String, OpenapiSchemaType)]): Seq[(String, Boolean, OpenapiSchemaType)] = {
      if (!isScala3) s.map { case (n, t) => (n, false, t) }
      else if (s.size != 1) s.map { case (n, t) => (n, true, t) }
      else if (isSimpleRecursive(s.head._1, s.head._2)) s.map { case (n, t) => (n, true, t) }
      else s.map { case (n, t) => (n, false, t) }
    }

    layers.foldLeft(Seq.empty[Seq[(String, Boolean, OpenapiSchemaType)]]) { (acc, next) =>
      val mappedNext = withRecursiveBoolean(next)
      if (acc.isEmpty) Seq(mappedNext)
      else if (acc.last.size + next.size >= maxLayerSize) acc :+ mappedNext
      else {
        val first :+ last = acc
        first :+ (last ++ mappedNext)
      }
    }
  }
  // Need to order rings so that leaf schemas are defined before parents
  private def orderLayers(layers: Seq[Seq[(String, OpenapiSchemaType)]]): Seq[Seq[(String, OpenapiSchemaType)]] = {
    def getDirectChildren(schema: OpenapiSchemaType): Set[String] = schema match {
      case r: OpenapiSchemaRef                                                                => Set(r.stripped)
      case _: OpenapiSchemaSimpleType | _: OpenapiSchemaEnum | _: OpenapiSchemaConstantString => Set.empty[String]
      case OpenapiSchemaArray(items, _, _, _)                                                 => getDirectChildren(items)
      case OpenapiSchemaNot(items)                                                            => getDirectChildren(items)
      case OpenapiSchemaMap(items, _, _)                                                      => getDirectChildren(items)
      case OpenapiSchemaOneOf(items, _)                                                       => items.flatMap(getDirectChildren).toSet
      case OpenapiSchemaAnyOf(items)                                                          => items.flatMap(getDirectChildren).toSet
      case OpenapiSchemaAllOf(items)                                                          => items.flatMap(getDirectChildren).toSet
      case OpenapiSchemaObject(kvs, _, _, _) => kvs.values.flatMap(f => getDirectChildren(f.`type`)).toSet
    }
    val withDirectChildren = layers.map { layer =>
      layer.map { case (k, v) => (k, v, getDirectChildren(v)) }
    }
    val initialSet: mutable.Set[Seq[(String, OpenapiSchemaType, Set[String])]] = mutable.Set(withDirectChildren: _*)
    val acquired = mutable.Set.empty[String]
    val res = mutable.ArrayBuffer.empty[Seq[(String, OpenapiSchemaType, Set[String])]]
    while (initialSet.nonEmpty) {
      // Find all schema 'rings' that depend only on 'already aquired' schemas and/or other members of the same ring
      val nextLayers = initialSet.filter(g => g.forall(_._3.forall(c => acquired.contains(c) || g.map(_._1).contains(c))))
      // remove these from the initial set, add to the 'acquired' set & res seq
      initialSet --= nextLayers
      // sorting here for output stability
      res ++= nextLayers.toSeq.sortBy(_.head._1)
      acquired ++= nextLayers.flatMap(_.map(_._1)).toSet
      if (initialSet.nonEmpty && nextLayers.isEmpty)
        throw new IllegalStateException(
          s"Cannot order layers until mutually-recursive references have been resolved. Unable to find all dependencies for ${initialSet.flatMap(_.map(_._1))}"
        )
    }

    res.map(_.map { case (k, v, _) => k -> v })
  }
  // finds all mutually-recursive references, grouping mutually-recursive schemas into a single 'layer' seq
  private def constructRings(allSchemas: Map[String, OpenapiSchemaType]): Seq[Seq[(String, OpenapiSchemaType)]] = {
    val initialSet: mutable.Set[(String, OpenapiSchemaType)] = mutable.Set(allSchemas.toSeq: _*)
    val res = mutable.ArrayBuffer.empty[Seq[(String, OpenapiSchemaType)]]
    while (initialSet.nonEmpty) {
      val nextRing = mutable.ArrayBuffer.empty[(String, OpenapiSchemaType)]
      def recurse(next: (String, OpenapiSchemaType)): Unit = {
        val (nextName, nextSchema) = next
        nextRing += next
        initialSet -= next
        // Find all simple reference loops for a single candidate
        val refs = getReferencesToXInY(allSchemas, nextName, nextSchema, Set.empty, Seq(nextName))
        val newRefs = refs.flatMap(r => initialSet.find(_._1 == r))
        // New candidates may themselves have mutually-recursive references to other candidates that _don't_ have
        // 'loop' references to initial candidate, so we need to recurse here - e.g for
        // `case class A(maybeB: Option[B])`, `case class B(maybeA: Option[A], maybeC: Option[C])`, `case class C(maybeB: Option[B])`
        // we have the loops A -> B -> A, and B -> C -> B, but the loop A -> B -> C -> B -> A would not be detected by `getReferencesToXInY`
        // Fusing all simple loops should be a valid way of constructing the equivalence set.
        newRefs foreach recurse
      }
      // Select next candidate. Order lexicographically for stable output
      val next = initialSet.minBy(_._1)
      recurse(next)
      res += nextRing.distinct.sortBy(_._1)
    }
    res.toSeq
  }

  private def isSimpleRecursive(schemaName: String, schema: OpenapiSchemaType): Boolean = schema match {
    case r: OpenapiSchemaRef => r.stripped == schemaName
    // these types cannot contain a reference
    case _: OpenapiSchemaSimpleType | _: OpenapiSchemaEnum | _: OpenapiSchemaConstantString => false
    // descend into the sole child type
    case OpenapiSchemaArray(items, _, _, _) => isSimpleRecursive(schemaName, items)
    case OpenapiSchemaNot(items)            => isSimpleRecursive(schemaName, items)
    case OpenapiSchemaMap(items, _, _)      => isSimpleRecursive(schemaName, items)
    // descend into all child types
    case OpenapiSchemaOneOf(items, _)      => items.exists(isSimpleRecursive(schemaName, _))
    case OpenapiSchemaAllOf(items)         => items.exists(isSimpleRecursive(schemaName, _))
    case OpenapiSchemaAnyOf(items)         => items.exists(isSimpleRecursive(schemaName, _))
    case OpenapiSchemaObject(kvs, _, _, _) => kvs.values.exists(v => isSimpleRecursive(schemaName, v.`type`))
  }
  // find all simple reference loops starting at a single schema (e.g. A -> B -> C -> A)
  private def getReferencesToXInY(
      allSchemas: Map[String, OpenapiSchemaType],
      referent: String, // The stripped ref of the schema we're looking for references to
      referenceCandidate: OpenapiSchemaType, // candidate for mutually-recursive reference
      checked: Set[String], // refs we've already checked
      maybeRefs: Seq[String] // chain of refs from referent -> [...maybeRefs] -> referenceCandidate
  ): Set[String] = referenceCandidate match {
    case ref: OpenapiSchemaRef =>
      val stripped = ref.stripped
      // in this case, we have a chain of references from referent -> [...maybeRefs] -> referent, creating a mutually-recursive loop
      if (stripped == referent) maybeRefs.toSet
      // if already checked, skip
      else if (checked contains stripped) Set.empty
      // else add the ref to 'maybeRefs' chain and descend
      else {
        allSchemas
          .get(ref.stripped)
          .map(getReferencesToXInY(allSchemas, referent, _, checked + stripped, maybeRefs :+ stripped))
          .toSet
          .flatten
      }
    // these types cannot contain a reference
    case _: OpenapiSchemaSimpleType | _: OpenapiSchemaEnum | _: OpenapiSchemaConstantString => Set.empty
    // descend into the sole child type
    case OpenapiSchemaArray(items, _, _, _) => getReferencesToXInY(allSchemas, referent, items, checked, maybeRefs)
    case OpenapiSchemaNot(items)            => getReferencesToXInY(allSchemas, referent, items, checked, maybeRefs)
    case OpenapiSchemaMap(items, _, _)      => getReferencesToXInY(allSchemas, referent, items, checked, maybeRefs)
    // descend into all child types
    case OpenapiSchemaOneOf(items, _) => items.flatMap(getReferencesToXInY(allSchemas, referent, _, checked, maybeRefs)).toSet
    case OpenapiSchemaAllOf(items)    => items.flatMap(getReferencesToXInY(allSchemas, referent, _, checked, maybeRefs)).toSet
    case OpenapiSchemaAnyOf(items)    => items.flatMap(getReferencesToXInY(allSchemas, referent, _, checked, maybeRefs)).toSet
    case OpenapiSchemaObject(kvs, _, _, _) =>
      kvs.values.flatMap(v => getReferencesToXInY(allSchemas, referent, v.`type`, checked, maybeRefs)).toSet
  }

  private def schemaForObject(name: String, isRecursive: Boolean, schema: OpenapiSchemaObject): String = {
    val subs = schema.properties.collect {
      case (k, OpenapiSchemaField(`type`: OpenapiSchemaObject, _, _)) => schemaForObject(s"$name${k.capitalize}", isRecursive, `type`)
      case (k, OpenapiSchemaField(OpenapiSchemaArray(`type`: OpenapiSchemaObject, _, _, _), _, _)) =>
        schemaForObject(s"$name${k.capitalize}Item", isRecursive, `type`)
      case (k, OpenapiSchemaField(OpenapiSchemaMap(`type`: OpenapiSchemaObject, _, _), _, _)) =>
        schemaForObject(s"$name${k.capitalize}Item", isRecursive, `type`)
      case (k, OpenapiSchemaField(_: OpenapiSchemaEnum, _, _)) => schemaForEnum(s"$name${k.capitalize}")
      case (k, OpenapiSchemaField(OpenapiSchemaArray(_: OpenapiSchemaEnum, _, _, _), _, _)) =>
        schemaForEnum(s"$name${k.capitalize}Item")
      case (k, OpenapiSchemaField(OpenapiSchemaMap(_: OpenapiSchemaEnum, _, _), _, _)) =>
        schemaForEnum(s"$name${k.capitalize}Item")
    } match {
      case s if s.isEmpty => ""
      case s              => s.mkString("", "\n", "\n")
    }
    s"${subs}implicit ${decl(isRecursive)} ${RootGenerator.uncapitalise(name)}TapirSchema: sttp.tapir.Schema[$name] = sttp.tapir.Schema.derived"
  }
  private def schemaForMapOrArray(name: String, isRecursive: Boolean, schema: OpenapiSchemaType): String = {
    val subs = schema match {
      case `type`: OpenapiSchemaObject => Some(schemaForObject(s"${name}ObjectsItem", isRecursive, `type`))
      case _                           => None
    }
    subs.fold("")("\n" + _)
  }
  private def schemaForEnum(name: String): String =
    s"""implicit lazy val ${RootGenerator.uncapitalise(name)}TapirSchema: sttp.tapir.Schema[$name] = sttp.tapir.Schema.derived"""

  private def genADTSchema(name: String, schema: OpenapiSchemaOneOf, isRecursive: Boolean, fullModelPath: Option[String]): String = {
    val schemaImpl = schema match {
      case OpenapiSchemaOneOf(_, None) => "sttp.tapir.Schema.derived"
      case OpenapiSchemaOneOf(_, Some(Discriminator(propertyName, maybeMapping))) =>
        val mapping =
          maybeMapping.map(_.map { case (propName, fullRef) => propName -> fullRef.stripPrefix("#/components/schemas/") }).getOrElse {
            schema.types.map {
              case ref: OpenapiSchemaRef => ref.stripped -> ref.stripped
              case other =>
                throw new IllegalArgumentException(s"oneOf subtypes must be refs to explicit schema models, found $other for $name")
            }.toMap
          }
        val fullModelPrefix = fullModelPath.map(_ + ".") getOrElse ""
        val fields = mapping
          .map { case (propValue, fullRef) =>
            val fullClassName = fullModelPrefix + fullRef
            s""""$propValue" -> sttp.tapir.SchemaType.SRef(sttp.tapir.Schema.SName("$fullClassName"))"""
          }
          .mkString(",\n")
        s"""{
           |  val derived = implicitly[sttp.tapir.generic.Derived[sttp.tapir.Schema[$name]]].value
           |  derived.schemaType match {
           |    case s: sttp.tapir.SchemaType.SCoproduct[_] => derived.copy(schemaType = s.addDiscriminatorField(
           |      sttp.tapir.FieldName("$propertyName"),
           |      sttp.tapir.Schema.string,
           |      Map(
           |${indent(8)(fields)}
           |      )
           |    ))
           |    case _ => throw new IllegalStateException("Derived schema for $name should be a coproduct")
           |  }
           |}""".stripMargin
    }

    s"implicit ${decl(isRecursive)} ${RootGenerator.uncapitalise(name)}TapirSchema: sttp.tapir.Schema[$name] = ${schemaImpl}"
  }
}
