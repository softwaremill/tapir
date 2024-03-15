package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.{indent, mapSchemaSimpleTypeToType}
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaEnum,
  OpenapiSchemaMap,
  OpenapiSchemaObject,
  OpenapiSchemaSimpleType
}

class ClassDefinitionGenerator {

  def classDefs(doc: OpenapiDocument, targetScala3: Boolean = false, queryParamRefs: Set[String] = Set.empty): Option[String] = {
    val generatesQueryParamEnums =
      doc.components.toSeq
        .flatMap(_.schemas.collect { case (name, _: OpenapiSchemaEnum) => name })
        .exists(queryParamRefs.contains)
    val enumQuerySerdeHelper =
      if (!generatesQueryParamEnums) ""
      else if (targetScala3)
        """
          |def enumMap[E: enumextensions.EnumMirror]: Map[String, E] =
          |  Map.from(
          |    for e <- enumextensions.EnumMirror[E].values yield e.name.toUpperCase -> e
          |  )
          |
          |def makeQueryCodecForEnum[T: enumextensions.EnumMirror]: sttp.tapir.Codec[List[String], T, sttp.tapir.CodecFormat.TextPlain] =
          |  sttp.tapir.Codec
          |    .listHead[String, String, sttp.tapir.CodecFormat.TextPlain]
          |    .mapDecode(s =>
          |      // Case-insensitive mapping
          |      scala.util
          |        .Try(enumMap[T](using enumextensions.EnumMirror[T])(s.toUpperCase))
          |        .fold(
          |          _ =>
          |            sttp.tapir.DecodeResult.Error(
          |              s,
          |              new NoSuchElementException(
          |                s"Could not find value $s for enum ${enumextensions.EnumMirror[T].mirroredName}, available values: ${enumextensions.EnumMirror[T].values.mkString(", ")}"
          |              )
          |            ),
          |          sttp.tapir.DecodeResult.Value(_)
          |        )
          |    )(_.name)
          |""".stripMargin
      else
        """def makeQueryCodecForEnum[T <: enumeratum.EnumEntry](enumName: String, T: enumeratum.Enum[T]): sttp.tapir.Codec[List[String], T, sttp.tapir.CodecFormat.TextPlain] =
          |  sttp.tapir.Codec.listHead[String, String, sttp.tapir.CodecFormat.TextPlain]
          |    .mapDecode(s =>
          |      // Case-insensitive mapping
          |      scala.util.Try(T.upperCaseNameValuesToMap(s.toUpperCase))
          |        .fold(
          |          _ =>
          |            sttp.tapir.DecodeResult.Error(
          |              s,
          |              new NoSuchElementException(
          |                s"Could not find value $s for enum ${enumName}, available values: ${T.values.mkString(", ")}"
          |              )
          |            ),
          |          sttp.tapir.DecodeResult.Value(_)
          |        )
          |    )(_.entryName)
          |""".stripMargin
    val defns = doc.components
      .map(_.schemas.flatMap {
        case (name, obj: OpenapiSchemaObject) =>
          generateClass(name, obj)
        case (name, obj: OpenapiSchemaEnum) =>
          generateEnum(name, obj, targetScala3, queryParamRefs)
        case (name, OpenapiSchemaMap(valueSchema, _)) => generateMap(name, valueSchema)
        case (n, x) => throw new NotImplementedError(s"Only objects, enums and maps supported! (for $n found ${x})")
      })
      .map(_.mkString("\n"))
    defns.map(enumQuerySerdeHelper + _)
  }

  private[codegen] def generateMap(name: String, valueSchema: OpenapiSchemaType): Seq[String] = {
    val valueSchemaName = valueSchema match {
      case simpleType: OpenapiSchemaSimpleType => BasicGenerator.mapSchemaSimpleTypeToType(simpleType)._1
      case otherType => throw new NotImplementedError(s"Only simple value types and refs are implemented for named maps (found $otherType)")
    }
    Seq(s"""type $name = Map[String, $valueSchemaName]""")
  }

  // Uses enumeratum for scala 2, but generates scala 3 enums instead where it can
  private[codegen] def generateEnum(
      name: String,
      obj: OpenapiSchemaEnum,
      targetScala3: Boolean,
      queryParamRefs: Set[String]
  ): Seq[String] = if (targetScala3) {
    val maybeQueryParamSerdeDerivation = if (queryParamRefs contains name) ", enumextensions.EnumMirror" else ""
    val maybeCompanion =
      if (queryParamRefs contains name)
        s"""
        |object $name {
        |  given stringList${name}Codec: sttp.tapir.Codec[List[String], $name, sttp.tapir.CodecFormat.TextPlain] =
        |    makeQueryCodecForEnum[$name]
        |}""".stripMargin
      else ""
    s"""$maybeCompanion
       |enum $name derives org.latestbit.circe.adt.codec.JsonTaggedAdt.PureCodec$maybeQueryParamSerdeDerivation {
       |  case ${obj.items.map(_.value).mkString(", ")}
       |}""".stripMargin :: Nil
  } else {
    val members = obj.items.map { i => s"case object ${i.value} extends $name" }
    val maybeQueryCodecDefn =
      if (queryParamRefs contains name)
        s"""
       |  implicit val ${name.head.toLower +: name.tail}Codec: sttp.tapir.Codec[List[String], ${name}, sttp.tapir.CodecFormat.TextPlain] =
       |    makeQueryCodecForEnum("${name}", ${name})""".stripMargin
      else ""
    s"""|sealed trait $name extends enumeratum.EnumEntry
        |object $name extends enumeratum.Enum[$name] with enumeratum.CirceEnum[$name] {
        |  val values = findValues
        |${indent(2)(members.mkString("\n"))}$maybeQueryCodecDefn
        |}""".stripMargin :: Nil
  }

  private[codegen] def generateClass(name: String, obj: OpenapiSchemaObject): Seq[String] = {
    def rec(name: String, obj: OpenapiSchemaObject, acc: List[String]): Seq[String] = {
      val innerClasses = obj.properties
        .collect {
          case (propName, st: OpenapiSchemaObject) =>
            val newName = addName(name, propName)
            rec(newName, st, Nil)

          case (propName, OpenapiSchemaMap(st: OpenapiSchemaObject, _)) =>
            val newName = addName(addName(name, propName), "item")
            rec(newName, st, Nil)

          case (propName, OpenapiSchemaArray(st: OpenapiSchemaObject, _)) =>
            val newName = addName(addName(name, propName), "item")
            rec(newName, st, Nil)
        }
        .flatten
        .toList

      val properties = obj.properties.map { case (key, schemaType) =>
        val tpe = mapSchemaTypeToType(name, key, obj.required.contains(key), schemaType)
        val fixedKey = fixKey(key)
        s"$fixedKey: $tpe"
      }

      s"""|case class $name (
          |${indent(2)(properties.mkString(",\n"))}
          |)""".stripMargin :: innerClasses ::: acc
    }

    rec(addName("", name), obj, Nil)
  }

  private def mapSchemaTypeToType(parentName: String, key: String, required: Boolean, schemaType: OpenapiSchemaType): String = {
    val (tpe, optional) = schemaType match {
      case simpleType: OpenapiSchemaSimpleType =>
        mapSchemaSimpleTypeToType(simpleType)

      case objectType: OpenapiSchemaObject =>
        addName(parentName, key) -> objectType.nullable

      case mapType: OpenapiSchemaMap =>
        val innerType = mapSchemaTypeToType(addName(parentName, key), "item", required = true, mapType.items)
        s"Map[String, $innerType]" -> mapType.nullable

      case arrayType: OpenapiSchemaArray =>
        val innerType = mapSchemaTypeToType(addName(parentName, key), "item", required = true, arrayType.items)
        s"Seq[$innerType]" -> arrayType.nullable

      case _ =>
        throw new NotImplementedError(s"We can't serialize some of the properties yet! $parentName $key $schemaType")
    }

    if (optional || !required) s"Option[$tpe]" else tpe
  }

  private def addName(parentName: String, key: String) = parentName + key.replace('_', ' ').replace('-', ' ').capitalize.replace(" ", "")

  private val reservedKeys = scala.reflect.runtime.universe.asInstanceOf[scala.reflect.internal.SymbolTable].nme.keywords.map(_.toString)

  private def fixKey(key: String) = {
    if (reservedKeys.contains(key))
      s"`$key`"
    else
      key
  }
}
