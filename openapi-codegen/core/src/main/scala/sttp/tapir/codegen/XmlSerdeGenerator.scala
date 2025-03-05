package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.indent
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaField,
  OpenapiSchemaObject,
  OpenapiSchemaSimpleType
}

object XmlSerdeGenerator {

  def generateSerdes(doc: OpenapiDocument, xmlParamRefs: Set[String]): Option[String] = {
    if (xmlParamRefs.isEmpty) None
    else
      Some {
        xmlParamRefs
          .map { ref =>
            val decoderName = s"${ref}XmlDecoder"
            val encoderName = s"${ref}XmlEncoder"
            val mappedArraysOfSimpleSchemas = doc.components.toSeq
              .flatMap(_.schemas.get(ref))
              .collect { case OpenapiSchemaObject(props, _, _) => props }
              .flatMap {
                _.collect { case (n, OpenapiSchemaField(OpenapiSchemaArray(t: OpenapiSchemaSimpleType, _), _)) =>
                  n -> BasicGenerator.mapSchemaSimpleTypeToType(t)._1
                }
              }
            // TODO: parse `xml` on schema and use it to configure these
            val maybeElemSeqDecoders = mappedArraysOfSimpleSchemas
              .map { case (n, t) => s"""implicit val $ref${n.capitalize}SeqDecoder: Decoder[Seq[$t]] = seqDecoder[$t]("$n")""" } match {
              case s if s.isEmpty => None
              case s              => Some(s.mkString("\n"))
            }
            val maybeElemSeqEncoders = mappedArraysOfSimpleSchemas
              .map { case (n, t) => s"""implicit val $ref${n.capitalize}SeqEncoder: Encoder[Seq[$t]] = seqEncoder[$t]("$n")""" } match {
              case s if s.isEmpty => None
              case s              => Some(s.mkString("\n"))
            }
            val decoderDefn = maybeElemSeqDecoders match {
              case None => s"deriveDecoder[$ref]"
              case Some(e) =>
                s"""{
                   |${indent(2)(e)}
                   |  deriveDecoder[$ref]
                   |}""".stripMargin
            }
            val encoderDefn = maybeElemSeqEncoders match {
              case None => s"deriveEncoder[$ref]"
              case Some(e) =>
                s"""{
                   |${indent(2)(e)}
                   |  deriveEncoder[$ref]
                   |}""".stripMargin
            }
            s"""
               |implicit lazy val ${ref}XmlTypeInterpreter: XmlTypeInterpreter[$ref] = XmlTypeInterpreter.auto[$ref](
               |  (_, tpeInfo) => false,
               |  (_, _) => false)
               |implicit lazy val $decoderName: Decoder[$ref] = $decoderDefn
               |implicit lazy val $encoderName: Encoder[$ref] = $encoderDefn
               |implicit lazy val ${ref}XmlSerde: sttp.tapir.Codec.XmlCodec[${ref.capitalize}] =
               |  sttp.tapir.Codec.xml(xmlToDecodeResult[$ref])(_.toXml.toString)""".stripMargin
          }
          .mkString("\n")
      }
  }
}
