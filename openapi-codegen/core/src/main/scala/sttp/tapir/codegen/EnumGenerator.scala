package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.indent
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
      jsonParamRefs: Set[String]
  ): Seq[String] = {
    def maybeEscaped(s: String) = s match {
      case legalEnumName(l) => l
      case illegal          => s"`$illegal`"
    }
    if (targetScala3) {
      val maybeCompanion =
        if (queryParamRefs contains name) {
          def helperImpls =
            s"""  given enumCodecSupport${name.capitalize}: ExtraParamSupport[$name] =
               |    extraCodecSupport[$name]""".stripMargin
          s"""
             |object $name {
             |$helperImpls
             |}""".stripMargin
        } else ""
      val maybeCodecExtensions = jsonSerdeLib match {
        case _ if !jsonParamRefs.contains(name) && !queryParamRefs.contains(name) => ""
        case _ if !jsonParamRefs.contains(name)                                   => " derives enumextensions.EnumMirror"
        case JsonSerdeLib.Circe | JsonSerdeLib.Jsoniter if !queryParamRefs.contains(name) =>
          " derives org.latestbit.circe.adt.codec.JsonTaggedAdt.PureCodec"
        case JsonSerdeLib.Circe | JsonSerdeLib.Jsoniter =>
          " derives org.latestbit.circe.adt.codec.JsonTaggedAdt.PureCodec, enumextensions.EnumMirror"
        case JsonSerdeLib.Zio if !queryParamRefs.contains(name) => s" extends java.lang.Enum[$name]"
        case JsonSerdeLib.Zio                                   => s" extends java.lang.Enum[$name] derives enumextensions.EnumMirror"
      }
      s"""$maybeCompanion
         |enum $name$maybeCodecExtensions {
         |  case ${obj.items.map(i => maybeEscaped(i.value)).mkString(", ")}
         |}""".stripMargin :: Nil
    } else {
      val members = obj.items.map { i => s"case object ${maybeEscaped(i.value)} extends $name" }
      val maybeCodecExtension = jsonSerdeLib match {
        case _ if !jsonParamRefs.contains(name) && !queryParamRefs.contains(name) => ""
        case JsonSerdeLib.Circe                                                   => s" with enumeratum.CirceEnum[$name]"
        case JsonSerdeLib.Jsoniter | JsonSerdeLib.Zio                             => ""
      }
      val maybeQueryCodecDefn =
        if (queryParamRefs contains name) {
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
}
