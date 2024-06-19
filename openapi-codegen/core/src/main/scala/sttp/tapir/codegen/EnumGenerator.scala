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
      jsonParamRefs: Set[String]
  ): Seq[String] = {
    if (targetScala3) {
      val maybeCompanion =
        if (queryParamRefs contains name) {
          def helperImpls =
            s"""  given plainList${name}Codec: sttp.tapir.Codec[List[String], $name, sttp.tapir.CodecFormat.TextPlain] =
               |    makeQueryCodecForEnum[$name]
               |  given plainListOpt${name}Codec: sttp.tapir.Codec[List[String], Option[$name], sttp.tapir.CodecFormat.TextPlain] =
               |    makeQueryOptCodecForEnum[$name]
               |  given plainListList${name}Codec: sttp.tapir.Codec[List[String], List[$name], sttp.tapir.CodecFormat.TextPlain] =
               |    makeQuerySeqCodecForEnum[$name]""".stripMargin
          s"""
             |object $name {
             |$helperImpls
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
        if (queryParamRefs contains name) {
          s"""
               |  implicit val ${uncapitalisedName}QueryCodec: sttp.tapir.Codec[List[String], $name, sttp.tapir.CodecFormat.TextPlain] =
               |    makeQueryCodecForEnum("${name}", ${name})
               |  implicit val ${uncapitalisedName}OptQueryCodec: sttp.tapir.Codec[List[String], Option[$name], sttp.tapir.CodecFormat.TextPlain] =
               |    makeQueryOptCodecForEnum("${name}", ${name})
               |  implicit val ${uncapitalisedName}SeqQueryCodec: sttp.tapir.Codec[List[String], List[$name], sttp.tapir.CodecFormat.TextPlain] =
               |    makeQuerySeqCodecForEnum("${name}", ${name})""".stripMargin
        } else ""
      s"""
         |sealed trait $name extends enumeratum.EnumEntry
         |object $name extends enumeratum.Enum[$name]$maybeCodecExtension {
         |  val values = findValues
         |${indent(2)(members.mkString("\n"))}$maybeQueryCodecDefn
         |}""".stripMargin :: Nil
    }
  }
}
