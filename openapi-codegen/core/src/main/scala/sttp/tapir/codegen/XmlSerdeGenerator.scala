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
import sttp.tapir.codegen.openapi.models.OpenapiXml

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
              .collect { case OpenapiSchemaObject(props, required, _, _) => props.map(p => p -> required.contains(p._1)) }
              .flatMap {
                _.collect {
                  case ((n, OpenapiSchemaField(t: OpenapiSchemaRef, _)), r)
                      if doc.components.exists(_.schemas.get(t.stripped).exists(_.isInstanceOf[OpenapiSchemaEnum])) =>
                    val tpe = BasicGenerator.mapSchemaSimpleTypeToType(t)._1
                    val d = if (!r || t.nullable) s"Option[$tpe]" else tpe
                    (n, d, tpe, 2, None)
                  case ((n, OpenapiSchemaField(OpenapiSchemaArray(t: OpenapiSchemaSimpleType, _, maybeXml), _)), _) =>
                    val tpe = BasicGenerator.mapSchemaSimpleTypeToType(t)._1
                    (n, tpe, tpe, 0, maybeXml)
                  case ((n, OpenapiSchemaField(t: OpenapiSchemaRef, _)), r) =>
                    val tpe = BasicGenerator.mapSchemaSimpleTypeToType(t)._1
                    val d = if (!r || t.nullable) s"Option[$tpe]" else tpe
                    (n, d, tpe, 1, None)
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
                case (n, t, _, 0, c: Option[OpenapiXml.XmlArrayConfiguration @unchecked]) =>
                  val name = c.flatMap(_.name).getOrElse(n)
                  val w = c.exists(_.isWrapped)
//                  val maybeP = if (w)
//                    s"""
//                       |""".stripMargin else ""
                  s"""implicit val $ref${n.capitalize}SeqDecoder: Decoder[Seq[$t]] = seqDecoder[$t]("$name", isWrapped = $w)"""
                case (n, t, _, 1, _) => s"""// implicit val $ref${n.capitalize}Decoder: Decoder[$t] = deriveConfiguredDecoder[$t]"""
                case (n, t, tpe, 2, _) if t == tpe =>
                  s"""implicit val $ref${n.capitalize}Decoder: Decoder[$t] = enumDecoder($ref${n.capitalize})"""
                case (n, t, tpe, 2, _) =>
                  s"""implicit val $ref${n.capitalize}OptionDecoder: Decoder[$t] = optionDecoder[$tpe](enumDecoder[$tpe]($tpe))""".stripMargin
              } match {
              case s if s.isEmpty => None
              case s              => Some(s.mkString("\n"))
            }
            val maybeElemSeqEncoders = mappedArraysOfSimpleSchemas
              .map {
                case (n, t, _, 0, c: Option[OpenapiXml.XmlArrayConfiguration @unchecked]) =>
                  // TODO: Parameterisation here must come from openapi
                  val in = c.flatMap(_.itemName).getOrElse(n)
                  val w = c.exists(_.isWrapped)
                  s"""implicit val $ref${n.capitalize}SeqEncoder: Encoder[Seq[$t]] =
                     |  seqEncoder[$t]("${c.flatMap(_.name).getOrElse(n)}", isWrapped = $w, itemName = "$in")""".stripMargin
                case (n, t, _, 1, _) =>
                  s"""implicit val $ref${n.capitalize}Encoder: Encoder[$t] = deriveConfiguredEncoder[$t]""".stripMargin
                case (n, t, tpe, 2, _) if t == tpe =>
                  s"""implicit val $ref${n.capitalize}Encoder: Encoder[$tpe] = enumEncoder[$tpe]("$n")""".stripMargin
                case (n, t, tpe, 2, _) =>
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
            // TODO: Should be able to rename non-array fields too
            val renamedFields = mappedArraysOfSimpleSchemas
              .collect { case (n, t, _, 0, c: Option[OpenapiXml.XmlArrayConfiguration @unchecked]) =>
                n -> c.flatMap(_.name)
              }
              .collect { case (n, Some(n2)) if n != n2 => n -> n2 }
            val interpreter =
              if (renamedFields.nonEmpty) {
                val cases = renamedFields
                  .map { case (from, to) =>
                    s"""case (cats.xml.utils.generic.ParamName("$from"), _) =>
                     |  (XmlElemType.Child, { case "$from" => "$to"; case "$to" => "$from"; case x => x})""".stripMargin
                  }
                  .mkString("\n")
                s"""XmlTypeInterpreter.fullOf[$ref]{
                   |${indent(4)(cases)}
                   |    case (_, _) => (XmlElemType.Child, identity)
                   |  }""".stripMargin
              } else s"""XmlTypeInterpreter.auto[$ref]((_, _) => false, (_, _) => false)""".stripMargin
            s"""
               |implicit lazy val ${ref}XmlTypeInterpreter: XmlTypeInterpreter[$ref] = $interpreter
               |implicit lazy val $decoderName: Decoder[$ref] = $decoderDefn
               |implicit lazy val $encoderName: Encoder[$ref] = $encoderDefn
               |implicit lazy val ${ref}XmlSerde: sttp.tapir.Codec.XmlCodec[${ref.capitalize}] =
               |  sttp.tapir.Codec.xml(xmlToDecodeResult[$ref])(_.toXml.toString)""".stripMargin
          }
          .mkString("\n")
      }
  }
}
