package sttp.tapir.codegen

import sttp.tapir.codegen.BasicGenerator.indent
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaEnum,
  OpenapiSchemaField,
  OpenapiSchemaObject,
  OpenapiSchemaRef,
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
              .collect { case OpenapiSchemaObject(props, required, _) => props.map(p => p -> required.contains(p._1)) }
              .flatMap {
                _.collect {
                  case ((n, OpenapiSchemaField(t: OpenapiSchemaRef, _)), r)
                      if doc.components.exists(_.schemas.get(t.stripped).exists(_.isInstanceOf[OpenapiSchemaEnum])) =>
                    val tpe = BasicGenerator.mapSchemaSimpleTypeToType(t)._1
                    val d = if (!r || t.nullable) s"Option[$tpe]" else tpe
                    (n, d, tpe, 2)
                  case ((n, OpenapiSchemaField(OpenapiSchemaArray(t: OpenapiSchemaSimpleType, _), _)), _) =>
                    val tpe = BasicGenerator.mapSchemaSimpleTypeToType(t)._1
                    (n, tpe, tpe, 0)
                  case ((n, OpenapiSchemaField(t: OpenapiSchemaRef, _)), r) =>
                    val tpe = BasicGenerator.mapSchemaSimpleTypeToType(t)._1
                    val d = if (!r || t.nullable) s"Option[$tpe]" else tpe
                    (n, d, tpe, 1)
//                  case ((n, OpenapiSchemaField(t: OpenapiSchemaEnum, _)), r) =>
                  //                    val tpe = BasicGenerator.mapSchemaSimpleTypeToType(t)._1
                  //                    val d = if (!r || t.nullable) s"Option[$tpe]" else tpe
                  //                    (n, d, 2)
                }
              }
              .distinct
            // TODO: parse `xml` on schema and use it to configure these
            val maybeElemSeqDecoders = mappedArraysOfSimpleSchemas
              .map {
                case (n, t, _, 0) => s"""implicit val $ref${n.capitalize}SeqDecoder: Decoder[Seq[$t]] = seqDecoder[$t]("$n")"""
                case (n, t, _, 1) => s"""// implicit val $ref${n.capitalize}Decoder: Decoder[$t] = deriveConfiguredDecoder[$t]"""
                case (n, t, tpe, 2) if t == tpe =>
                  s"""implicit val $ref${n.capitalize}Decoder: Decoder[$t] = enumDecoder($ref${n.capitalize})"""
                case (n, t, tpe, 2) =>
                  s"""implicit val $ref${n.capitalize}OptionDecoder: Decoder[$t] = optionDecoder[$tpe](enumDecoder[$tpe]($tpe))""".stripMargin
              } match {
              case s if s.isEmpty => None
              case s              => Some(s.mkString("\n"))
            }
            val maybeElemSeqEncoders = mappedArraysOfSimpleSchemas
              .map {
                case (n, t, _, 0) =>
                  // TODO: Parameterisation here must come from openapi
                  val isWrapped = true
                  val itemName = n
                  s"""implicit val $ref${n.capitalize}SeqEncoder: Encoder[Seq[$t]] = seqEncoder[$t]("$n", isWrapped = $isWrapped, itemName = "${itemName}")"""
                case (n, t, _, 1) => s"""implicit val $ref${n.capitalize}Encoder: Encoder[$t] = deriveConfiguredEncoder[$t]""".stripMargin
                case (n, t, tpe, 2) if t == tpe =>
                  s"""implicit val $ref${n.capitalize}Encoder: Encoder[$tpe] = enumEncoder[$tpe]("$n")""".stripMargin
                case (n, t, tpe, 2) =>
                  s"""implicit val $ref${n.capitalize}Encoder: Encoder[$tpe] = enumEncoder[$tpe]("$n")
                     |implicit val $ref${n.capitalize}OptionEncoder: Encoder[$t] = optionEncoder[$tpe]($ref${n.capitalize}Encoder)""".stripMargin
              } match {
              case s if s.isEmpty => None
              case s              => Some(s.mkString("\n"))
            }
            val decoderDefn = maybeElemSeqDecoders match {
              case None => s"deriveConfiguredDecoder[$ref]"
              case Some(e) =>
                s"""{
                   |${indent(2)(e)}
                   |  deriveConfiguredDecoder[$ref]
                   |}""".stripMargin
            }
            val encoderDefn = maybeElemSeqEncoders match {
              case None => s"deriveConfiguredEncoder[$ref]"
              case Some(e) =>
                s"""{
                   |${indent(2)(e)}
                   |  deriveConfiguredEncoder[$ref]
                   |}""".stripMargin
            }
            // TODO: Attribute configuration here should come from xml fields of openapi
            s"""
               |implicit lazy val ${ref}XmlTypeInterpreter: XmlTypeInterpreter[$ref] = XmlTypeInterpreter.auto[$ref](
               |  (_, _) => false, (_, _) => false)
               |implicit lazy val $decoderName: Decoder[$ref] = $decoderDefn
               |implicit lazy val $encoderName: Encoder[$ref] = $encoderDefn
               |implicit lazy val ${ref}XmlSerde: sttp.tapir.Codec.XmlCodec[${ref.capitalize}] =
               |  sttp.tapir.Codec.xml(xmlToDecodeResult[$ref])(_.toXml.toString)""".stripMargin
          }
          .mkString("\n")
      }
  }
}
