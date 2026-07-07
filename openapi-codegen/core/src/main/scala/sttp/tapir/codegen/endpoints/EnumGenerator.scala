package sttp.tapir.codegen.endpoints

import sttp.tapir.codegen.util.NameHelpers.indent
import sttp.tapir.codegen.json.JsonSerdeLib
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.OpenapiSchemaEnum

object EnumGenerator {
  val legalEnumName = "([a-zA-Z][a-zA-Z0-9_]*)".r

  // Uses enumeratum for scala 2, but generates scala 3 enums instead where it can
  private[codegen] def generateEnum(
      name: String,
      obj: OpenapiSchemaEnum,
      targetScala3: Boolean,
      queryParamRefs: Set[String],
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib,
      jsonParamRefs: Set[String],
      alwaysGenerateParamSupport: Boolean
  ): Seq[String] = {
    def maybeEscaped(s: String) = s match {
      case legalEnumName(l) => l
      case illegal          => s"`$illegal`"
    }
    if (targetScala3) {
      val maybeCompanion =
        if (alwaysGenerateParamSupport || queryParamRefs.contains(name)) {
          def helperImpls =
            s"""  given enumCodecSupport${name.capitalize}: ExtraParamSupport[$name] =
               |    extraCodecSupport[$name]""".stripMargin
          s"""
             |object $name {
             |$helperImpls
             |}""".stripMargin
        } else ""
      val maybeCodecExtensions = jsonSerdeLib match {
        case _ if !alwaysGenerateParamSupport && !jsonParamRefs.contains(name) && !queryParamRefs.contains(name) =>
          ""
        case _ if !alwaysGenerateParamSupport && !jsonParamRefs.contains(name) =>
          " derives enumextensions.EnumMirror"
        case JsonSerdeLib.Circe if !alwaysGenerateParamSupport && !queryParamRefs.contains(name) =>
          " derives org.latestbit.circe.adt.codec.JsonTaggedAdt.PureCodec"
        case JsonSerdeLib.Circe =>
          " derives org.latestbit.circe.adt.codec.JsonTaggedAdt.PureCodec, enumextensions.EnumMirror"
        case JsonSerdeLib.Jsoniter if !alwaysGenerateParamSupport && !queryParamRefs.contains(name) =>
          ""
        case JsonSerdeLib.Jsoniter =>
          " derives enumextensions.EnumMirror"
        case JsonSerdeLib.Zio if !alwaysGenerateParamSupport && !queryParamRefs.contains(name) =>
          " derives zio.json.JsonCodec"
        case JsonSerdeLib.Zio =>
          " derives zio.json.JsonCodec, enumextensions.EnumMirror"
      }
      s"""$maybeCompanion
         |enum $name$maybeCodecExtensions {
         |  case ${obj.items.map(i => maybeEscaped(i.value)).mkString(", ")}
         |}""".stripMargin :: Nil
    } else {
      val members = obj.items.map { i => s"case object ${maybeEscaped(i.value)} extends $name" }
      val maybeCodecExtension = jsonSerdeLib match {
        case _ if !alwaysGenerateParamSupport && !jsonParamRefs.contains(name) && !queryParamRefs.contains(name) =>
          ""
        case JsonSerdeLib.Circe =>
          s" with enumeratum.CirceEnum[$name]"
        case JsonSerdeLib.Jsoniter | JsonSerdeLib.Zio =>
          ""
      }
      val maybeQueryCodecDefn =
        if (alwaysGenerateParamSupport || queryParamRefs.contains(name)) {
          s"""
               |  implicit val enumCodecSupport${name.capitalize}: ExtraParamSupport[$name] =
               |    extraCodecSupport[$name]("${name}", ${name})""".stripMargin
        } else ""
      s"""
         |sealed trait $name extends enumeratum.EnumEntry
         |object $name extends enumeratum.Enum[$name]$maybeCodecExtension {
         |  val values = findValues
         |${indent(2)(members.mkString("\n"))}$maybeQueryCodecDefn
         |}""".stripMargin :: Nil
    }
  }

  def enumSerdeHelperDefn(targetScala3: Boolean): String = {
    if (targetScala3)
      """
        |def enumMap[E: enumextensions.EnumMirror]: Map[String, E] =
        |  Map.from(
        |    for e <- enumextensions.EnumMirror[E].values yield e.name.toUpperCase -> e
        |  )
        |case class EnumExtraParamSupport[T: enumextensions.EnumMirror](eMap: Map[String, T]) extends ExtraParamSupport[T] {
        |  // Case-insensitive mapping
        |  def decode(s: String): sttp.tapir.DecodeResult[T] =
        |    scala.util
        |      .Try(eMap(s.toUpperCase))
        |      .fold(
        |        _ =>
        |          sttp.tapir.DecodeResult.Error(
        |            s,
        |            new NoSuchElementException(
        |              s"Could not find value $s for enum ${enumextensions.EnumMirror[T].mirroredName}, available values: ${enumextensions.EnumMirror[T].values.mkString(", ")}"
        |            )
        |          ),
        |        sttp.tapir.DecodeResult.Value(_)
        |      )
        |  def encode(t: T): String = t.name
        |}
        |def extraCodecSupport[T: enumextensions.EnumMirror]: ExtraParamSupport[T] =
        |  EnumExtraParamSupport(enumMap[T](using enumextensions.EnumMirror[T]))
        |""".stripMargin
    else
      """
        |case class EnumExtraParamSupport[T <: enumeratum.EnumEntry](enumName: String, T: enumeratum.Enum[T]) extends ExtraParamSupport[T] {
        |  // Case-insensitive mapping
        |  def decode(s: String): sttp.tapir.DecodeResult[T] =
        |    scala.util.Try(T.upperCaseNameValuesToMap(s.toUpperCase))
        |      .fold(
        |        _ =>
        |          sttp.tapir.DecodeResult.Error(
        |            s,
        |            new NoSuchElementException(
        |              s"Could not find value $s for enum ${enumName}, available values: ${T.values.mkString(", ")}"
        |            )
        |          ),
        |        sttp.tapir.DecodeResult.Value(_)
        |      )
        |  def encode(t: T): String = t.entryName
        |}
        |def extraCodecSupport[T <: enumeratum.EnumEntry](enumName: String, T: enumeratum.Enum[T]): ExtraParamSupport[T] =
        |  EnumExtraParamSupport(enumName, T)
        |""".stripMargin
  }

}
