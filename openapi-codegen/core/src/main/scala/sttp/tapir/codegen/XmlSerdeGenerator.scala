package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument

object XmlSerdeGenerator {

  def generateSerdes(doc: OpenapiDocument, xmlParamRefs: Set[String]): Option[String] = {
    if (xmlParamRefs.isEmpty) None
    else
      Some {
        xmlParamRefs
          .map(ref => s"""implicit lazy val ${ref}XmlSerde: sttp.tapir.Codec.XmlCodec[${ref.capitalize}] =
           |  sttp.tapir.Codec.xml(s => sttp.tapir.DecodeResult.Missing)(_ => "")""".stripMargin)
          .mkString("\n")
      }
  }
}
