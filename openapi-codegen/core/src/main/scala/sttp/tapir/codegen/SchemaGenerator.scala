package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.indent
import sttp.tapir.codegen.JsonSerdeLib.JsonSerdeLib
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  Discriminator,
  OpenapiSchemaAllOf,
  OpenapiSchemaAny,
  OpenapiSchemaAnyOf,
  OpenapiSchemaArray,
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

  def generateSchemas(
      doc: OpenapiDocument,
      allSchemas: Map[String, OpenapiSchemaType],
      fullModelPath: String,
      jsonSerdeLib: JsonSerdeLib,
      maxSchemasPerFile: Int
  ): Seq[String] = {
    def schemaContainsAny(schema: OpenapiSchemaType): Boolean = schema match {
      case _: OpenapiSchemaAny           => true
      case OpenapiSchemaArray(items, _)  => schemaContainsAny(items)
      case OpenapiSchemaMap(items, _)    => schemaContainsAny(items)
      case OpenapiSchemaObject(fs, _, _) => fs.values.map(_.`type`).exists(schemaContainsAny)
      case OpenapiSchemaOneOf(types, _)  => types.exists(schemaContainsAny)
      case OpenapiSchemaAllOf(types)     => types.exists(schemaContainsAny)
      case OpenapiSchemaAnyOf(types)     => types.exists(schemaContainsAny)
      case OpenapiSchemaNot(item)        => schemaContainsAny(item)
      case _: OpenapiSchemaSimpleType | _: OpenapiSchemaEnum | _: OpenapiSchemaConstantString | _: OpenapiSchemaRef => false
    }
    val schemasWithAny = allSchemas.filter { case (_, schema) =>
      schemaContainsAny(schema)
    }
    val maybeAnySchema: Option[(OpenapiSchemaType, String)] =
      if (schemasWithAny.isEmpty) None
      else if (jsonSerdeLib == JsonSerdeLib.Circe)
        Some(
          OpenapiSchemaAny(
            false
          ) -> "implicit lazy val anyTapirSchema: sttp.tapir.Schema[io.circe.Json] = sttp.tapir.Schema.any[io.circe.Json]"
        )
      else throw new NotImplementedError("any not implemented for json libs other than circe")
    val openApiSchemasWithTapirSchemas = doc.components
      .map(_.schemas.map {
        case (name, _: OpenapiSchemaEnum) =>
          name -> s"implicit lazy val ${BasicGenerator.uncapitalise(name)}TapirSchema: sttp.tapir.Schema[$name] = sttp.tapir.Schema.derived"
        case (name, obj: OpenapiSchemaObject) => name -> schemaForObject(name, obj)
        case (name, schema: OpenapiSchemaMap) => name -> schemaForMap(name, schema)
        case (name, schema: OpenapiSchemaOneOf) =>
          name -> genADTSchema(name, schema, if (fullModelPath.isEmpty) None else Some(fullModelPath))
        case (n, x) => throw new NotImplementedError(s"Only objects, enums, maps and oneOf supported! (for $n found ${x})")
      })
      .toSeq
      .flatMap(maybeAnySchema.toSeq ++ _)
      .toMap

    val groupedByRing = constructRings(allSchemas)
    val orderedLayers = orderLayers(groupedByRing)
    val foldedLayers = foldLayers(maxSchemasPerFile)(orderedLayers)
    maybeAnySchema.map(_._2).toSeq ++ foldedLayers.map(ring => ring.map(openApiSchemasWithTapirSchemas apply _._1).mkString("\n"))
  }
  // Group files into chunks
  private def foldLayers(maxSchemasPerFile: Int)(layers: Seq[Seq[(String, OpenapiSchemaType)]]): Seq[Seq[(String, OpenapiSchemaType)]] = {
    val maxLayerSize = maxSchemasPerFile
    layers.foldLeft(Seq.empty[Seq[(String, OpenapiSchemaType)]]) { (acc, next) =>
      if (acc.isEmpty) Seq(next)
      else if (acc.last.size + next.size >= maxLayerSize) acc :+ next
      else {
        val first :+ last = acc
        first :+ (last ++ next)
      }
    }
  }
  // Need to order rings so that leaf schemas are defined before parents
  private def orderLayers(layers: Seq[Seq[(String, OpenapiSchemaType)]]): Seq[Seq[(String, OpenapiSchemaType)]] = {
    def getDirectChildren(schema: OpenapiSchemaType): Set[String] = schema match {
      case r: OpenapiSchemaRef                                                                => Set(r.stripped)
      case _: OpenapiSchemaSimpleType | _: OpenapiSchemaEnum | _: OpenapiSchemaConstantString => Set.empty[String]
      case OpenapiSchemaArray(items, _)                                                       => getDirectChildren(items)
      case OpenapiSchemaNot(items)                                                            => getDirectChildren(items)
      case OpenapiSchemaMap(items, _)                                                         => getDirectChildren(items)
      case OpenapiSchemaOneOf(items, _)                                                       => items.flatMap(getDirectChildren).toSet
      case OpenapiSchemaAnyOf(items)                                                          => items.flatMap(getDirectChildren).toSet
      case OpenapiSchemaAllOf(items)                                                          => items.flatMap(getDirectChildren).toSet
      case OpenapiSchemaObject(kvs, _, _) => kvs.values.flatMap(f => getDirectChildren(f.`type`)).toSet
    }
    val withDirectChildren = layers.map { layer =>
      layer.map { case (k, v) => (k, v, getDirectChildren(v)) }
    }
    val initialSet: mutable.Set[Seq[(String, OpenapiSchemaType, Set[String])]] = mutable.Set(withDirectChildren: _*)
    val acquired = mutable.Set.empty[String]
    val res = mutable.ArrayBuffer.empty[Seq[(String, OpenapiSchemaType, Set[String])]]
    while (initialSet.nonEmpty) {
      val nextLayers = initialSet.filter(g => g.forall(_._3.forall(c => acquired.contains(c) || g.map(_._1).contains(c))))

      initialSet --= nextLayers
      res ++= nextLayers
      acquired ++= nextLayers.flatMap(_.map(_._1)).toSet
      if (initialSet.nonEmpty && nextLayers.isEmpty)
        throw new IllegalStateException("Cannot order layers until mutually-recursive references have been resolved.")
    }

    res.map(_.map { case (k, v, _) => k -> v })
  }
  // finds all recursive and mutually-recursive references, grouping mutually-recursive schemas into a single 'layer' seq
  private def constructRings(allSchemas: Map[String, OpenapiSchemaType]): Seq[Seq[(String, OpenapiSchemaType)]] = {
    val initialSet: mutable.Set[(String, OpenapiSchemaType)] = mutable.Set(allSchemas.toSeq: _*)
    val res = mutable.ArrayBuffer.empty[Seq[(String, OpenapiSchemaType)]]
    while (initialSet.nonEmpty) {
      val nextRing = mutable.ArrayBuffer.empty[(String, OpenapiSchemaType)]
      def recurse(next: (String, OpenapiSchemaType)): Unit = {
        val (nextName, nextSchema) = next
        nextRing += next
        initialSet -= next
        val refs = getReferencesToXInY(allSchemas, nextName, nextSchema, Set.empty, Seq(nextName))
        val newRefs = refs.flatMap(r => initialSet.find(_._1 == r))
        newRefs foreach recurse
      }
      // need to check oneOfs first because the children don't know they have parents. Order lexicographically for stable output
      val next = initialSet.filter(_._2.isInstanceOf[OpenapiSchemaOneOf]).toSeq.sortBy(_._1).headOption getOrElse initialSet.minBy(_._1)
      recurse(next)
      res += nextRing
    }
    res.toSeq
  }
  // finds recursive and mutually-recursive references to a single schema
  private def getReferencesToXInY(
      allSchemas: Map[String, OpenapiSchemaType],
      schemaX: String,
      schemaY: OpenapiSchemaType,
      checked: Set[String],
      maybeRefs: Seq[String]
  ): Set[String] = schemaY match {
    case ref: OpenapiSchemaRef =>
      val stripped = ref.stripped
      if (stripped == schemaX) maybeRefs.toSet
      else if (checked contains stripped) Set.empty
      else {
        allSchemas
          .get(ref.stripped)
          .map(getReferencesToXInY(allSchemas, schemaX, _, checked + stripped, maybeRefs :+ stripped))
          .toSet
          .flatten
      }
    case _: OpenapiSchemaSimpleType | _: OpenapiSchemaEnum | _: OpenapiSchemaConstantString => Set.empty
    case OpenapiSchemaArray(items, _) => getReferencesToXInY(allSchemas, schemaX, items, checked, maybeRefs)
    case OpenapiSchemaNot(items)      => getReferencesToXInY(allSchemas, schemaX, items, checked, maybeRefs)
    case OpenapiSchemaMap(items, _)   => getReferencesToXInY(allSchemas, schemaX, items, checked, maybeRefs)
    case OpenapiSchemaOneOf(items, _) => items.flatMap(getReferencesToXInY(allSchemas, schemaX, _, checked, maybeRefs)).toSet
    case OpenapiSchemaAllOf(items)    => items.flatMap(getReferencesToXInY(allSchemas, schemaX, _, checked, maybeRefs)).toSet
    case OpenapiSchemaAnyOf(items)    => items.flatMap(getReferencesToXInY(allSchemas, schemaX, _, checked, maybeRefs)).toSet
    case OpenapiSchemaObject(kvs, _, _) =>
      kvs.values.flatMap(v => getReferencesToXInY(allSchemas, schemaX, v.`type`, checked, maybeRefs)).toSet
  }

  private def schemaForObject(name: String, schema: OpenapiSchemaObject): String = {
    val subs = schema.properties.collect {
      case (k, OpenapiSchemaField(`type`: OpenapiSchemaObject, _)) => schemaForObject(s"$name${k.capitalize}", `type`)
      case (k, OpenapiSchemaField(OpenapiSchemaArray(`type`: OpenapiSchemaObject, _), _)) =>
        schemaForObject(s"$name${k.capitalize}Item", `type`)
      case (k, OpenapiSchemaField(OpenapiSchemaMap(`type`: OpenapiSchemaObject, _), _)) =>
        schemaForObject(s"$name${k.capitalize}Item", `type`)
    } match {
      case Nil => ""
      case s   => s.mkString("", "\n", "\n")
    }
    s"${subs}implicit lazy val ${BasicGenerator.uncapitalise(name)}TapirSchema: sttp.tapir.Schema[$name] = sttp.tapir.Schema.derived"
  }
  private def schemaForMap(name: String, schema: OpenapiSchemaMap): String = {
    val subs = schema.items match {
      case `type`: OpenapiSchemaObject => Some(schemaForObject(s"${name}ObjectsItem", `type`))
      case _                           => None
    }
    subs.fold("")("\n" + _)
  }
  private def genADTSchema(name: String, schema: OpenapiSchemaOneOf, fullModelPath: Option[String]): String = {
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

    s"implicit lazy val ${BasicGenerator.uncapitalise(name)}TapirSchema: sttp.tapir.Schema[$name] = ${schemaImpl}"
  }
}
