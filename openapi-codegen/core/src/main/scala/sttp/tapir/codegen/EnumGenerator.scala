package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.indent
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.OpenapiSchemaEnum

object EnumGenerator {

  // Uses enumeratum for scala 2, but generates scala 3 enums instead where it can
  private[codegen] def generateEnum(
      name: String,
      obj: OpenapiSchemaEnum,
      targetScala3: Boolean,
      queryParamRefs: Set[String],
      jsonSerdeLib: JsonSerdeLib.JsonSerdeLib,
      jsonParamRefs: Set[String],
      isArray: Boolean = false
  ): Seq[String] = {
    def helperName = if (isArray) "makeQuerySeqCodecForEnum" else "makeQueryCodecForEnum"
    def highLevelType = if (isArray) s"List[$name]" else name
    if (targetScala3) {
      val maybeCompanion =
        if (queryParamRefs contains name) {
          s"""
             |object $name {
             |  given stringList${name}Codec: sttp.tapir.Codec[List[String], $highLevelType, sttp.tapir.CodecFormat.TextPlain] =
             |    $helperName[$name]
             |}""".stripMargin
        } else ""
      val maybeCodecExtensions = jsonSerdeLib match {
        case _ if !jsonParamRefs.contains(name) && !queryParamRefs.contains(name) => ""
        case _ if !jsonParamRefs.contains(name)                                   => " derives enumextensions.EnumMirror"
        case JsonSerdeLib.Circe if !queryParamRefs.contains(name) => " derives org.latestbit.circe.adt.codec.JsonTaggedAdt.PureCodec"
        case JsonSerdeLib.Circe => " derives org.latestbit.circe.adt.codec.JsonTaggedAdt.PureCodec, enumextensions.EnumMirror"
        case JsonSerdeLib.Jsoniter | JsonSerdeLib.Zio if !queryParamRefs.contains(name) => s" extends java.lang.Enum[$name]"
        case JsonSerdeLib.Jsoniter | JsonSerdeLib.Zio => s" extends java.lang.Enum[$name] derives enumextensions.EnumMirror"
      }
      s"""$maybeCompanion
         |enum $name$maybeCodecExtensions {
         |  case ${obj.items.map(_.value).mkString(", ")}
         |}""".stripMargin :: Nil
    } else {
      val uncapitalisedName = BasicGenerator.uncapitalise(name)
      val members = obj.items.map { i => s"case object ${i.value} extends $name" }
      val maybeCodecExtension = jsonSerdeLib match {
        case _ if !jsonParamRefs.contains(name) && !queryParamRefs.contains(name) => ""
        case JsonSerdeLib.Circe                                                   => s" with enumeratum.CirceEnum[$name]"
        case JsonSerdeLib.Jsoniter | JsonSerdeLib.Zio                             => ""
      }
      val maybeQueryCodecDefn =
        if (queryParamRefs contains name)
          s"""
             |  implicit val ${uncapitalisedName}QueryCodec: sttp.tapir.Codec[List[String], ${highLevelType}, sttp.tapir.CodecFormat.TextPlain] =
             |    $helperName("${name}", ${name})""".stripMargin
        else ""
      s"""
         |sealed trait $name extends enumeratum.EnumEntry
         |object $name extends enumeratum.Enum[$name]$maybeCodecExtension {
         |  val values = findValues
         |${indent(2)(members.mkString("\n"))}$maybeQueryCodecDefn
         |}""".stripMargin :: Nil
    }
  }
}
