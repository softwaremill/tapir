package sttp.tapir.codegen.endpoints

import sttp.tapir.codegen.dedup.PackageReuseContext
import sttp.tapir.codegen.endpoints.Position._
import sttp.tapir.codegen.endpoints.SimpleTypes.mapSchemaSimpleTypeToType
import sttp.tapir.codegen.openapi.models.GenerationDirectives.{
  forceEager,
  forceReqEager,
  forceReqStreaming,
  forceRespEager,
  forceRespStreaming,
  forceStreaming,
  jsonBodyAsString
}
import sttp.tapir.codegen.openapi.models.{DefaultValueRenderer, OpenapiSchemaType, RenderConfig}
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaBinary,
  OpenapiSchemaMap,
  OpenapiSchemaObject,
  OpenapiSchemaRef,
  OpenapiSchemaSimpleType,
  OpenapiSchemaString
}
import sttp.tapir.codegen.util.ErrUtils.bail
import sttp.tapir.codegen.util.Location
import sttp.tapir.codegen.util.NameHelpers.{codecFormatName, indent, safeVariableName}
import sttp.tapir.codegen.validation.ValidationDefns
import sttp.tapir.codegen.xml.{XmlSerdeGenerator, XmlSerdeLib}
import sttp.tapir.codegen.xml.XmlSerdeLib.XmlSerdeLib

object InAndOutComponents {
  private[endpoints] def capabilityImpl(streamingImplementation: StreamingImplementation): String = streamingImplementation match {
    case Akka            => "sttp.capabilities.akka.AkkaStreams"
    case FS2(effectType) => s"sttp.capabilities.fs2.Fs2Streams[$effectType]"
    case Pekko           => "sttp.capabilities.pekko.PekkoStreams"
    case Zio             => "sttp.capabilities.zio.ZioStreams"
  }
  private[endpoints] def capabilityType(streamingImplementation: StreamingImplementation): String = streamingImplementation match {
    case FS2(effectType) => s"fs2.Stream[$effectType, Byte]"
    case x               => s"${capabilityImpl(x)}.BinaryStream"
  }
  private[endpoints] def combine(inlineDefn1: Option[String], inlineDefn2: Option[String], separator: String = "\n") =
    (inlineDefn1, inlineDefn2) match {
      case (None, None)               => None
      case (Some(defn), None)         => Some(defn)
      case (None, Some(defn))         => Some(defn)
      case (Some(defn1), Some(defn2)) => Some(defn1 + separator + defn2)
    }
  // These types all use 'eager' schemas, except for '*/*', which we default to eager for convenience but which has no schema mappings
  private[endpoints] val eagerTypes = Set("application/json", "application/xml", "text/plain", "text/html", "multipart/form-data", "*/*")

  private[endpoints] def aliases(packageReuse: PackageReuseContext, types: Seq[String], seperateFilesForModels: Boolean): String =
    types.map(PackageReuseContext.enumAliasType(_, packageReuse, seperateFilesForModels)).mkString("\n")

  private[endpoints] def contentTypeMapper(
      contentType: String,
      schema: OpenapiSchemaType,
      streamingImplementation: StreamingImplementation,
      required: Boolean,
      endpointName: String,
      position: Position,
      defaultEager: Boolean,
      xmlSerdeLib: XmlSerdeLib,
      tapirCodegenDirectives: Set[String],
      validators: ValidationDefns
  )(implicit location: Location): MappedContentType = {
    def vRef(t: OpenapiSchemaType, r: Boolean) = {
      val maybeValidatorRef = t match {
        case r: OpenapiSchemaRef => Some(r.stripped)
        // TODO: Validation on inline defns
        case _ => None
      }
      maybeValidatorRef
        .flatMap(validators.defns.get)
        .map(_.name)
        .map {
          case n if !r => s".validateOption(${n}Validator)"
          case n       => s".validate(${n}Validator)"
        }
        .getOrElse("")
    }
    def v(r: Boolean) = vRef(schema, r)
    val streaming = (tapirCodegenDirectives.contains(forceStreaming) && position != Err) ||
      (tapirCodegenDirectives.contains(forceReqStreaming) && position == Request) ||
      (tapirCodegenDirectives.contains(forceRespStreaming) && position == Response)
    val eager = !streaming && (defaultEager ||
      position == Err ||
      (tapirCodegenDirectives.contains(forceEager) && position != Err) ||
      (tapirCodegenDirectives.contains(forceReqEager) && position == Request) ||
      (tapirCodegenDirectives.contains(forceRespEager) && position == Response))
    contentType match {
      case any if streaming =>
        failoverBinaryCase(endpointName, position, any, schema, false, streamingImplementation)
      case "text/plain" =>
        MappedContentType("stringBody", "String")
      case "text/html" =>
        MappedContentType("htmlBodyUtf8", "String")
      case "application/xml" if xmlSerdeLib != XmlSerdeLib.NoSupport =>
        val (outT: String, maybeInline: Option[String], maybeAlias: Option[String], maybeTpe: Seq[String]) = schema match {
          case st: OpenapiSchemaSimpleType =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            (t, None, None, Nil)
          case a @ OpenapiSchemaArray(st: OpenapiSchemaSimpleType, _, _, _) =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            val tpeName = endpointName.capitalize + position
            val xmlSeqConfig = XmlSerdeGenerator.genTopLevelSeqSerdes(xmlSerdeLib, a, endpointName, position.toString).map(_._2)
            (s"List[$t]", xmlSeqConfig, Some(tpeName), xmlSeqConfig.toSeq.map(_ => tpeName))
          case x => bail(s"Only ref, primitive (and arrays of either) schemas supported for xml body (found $x)")
        }
        val req = if (required) outT else s"Option[$outT]"
        def toList = if (required) ".toList" else ".map(_.toList)"
        val bodyType = maybeAlias.map(a => s"xmlBody[$a].map(_.asInstanceOf[$req]$toList)(_.asInstanceOf[$a])").getOrElse(s"xmlBody[$req]")
        MappedContentType(bodyType + v(required), req, maybeInline, maybeTpe)
      case "application/json" if tapirCodegenDirectives.contains(jsonBodyAsString) =>
        if (required) MappedContentType("stringJsonBody", "String", None)
        else MappedContentType("stringJsonBody.map(Option(_))(_.orNull)", "Option[String]", None)
      case "application/json" =>
        val (outT, maybeInline) = schema match {
          case st: OpenapiSchemaSimpleType =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            t -> None
          case OpenapiSchemaArray(st: OpenapiSchemaSimpleType, _, _, _) =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            s"List[$t]" -> None
          case OpenapiSchemaMap(st: OpenapiSchemaSimpleType, _, _) =>
            val (t, _) = mapSchemaSimpleTypeToType(st)
            s"Map[String, $t]" -> None
          case schemaRef: OpenapiSchemaObject if schemaRef.properties.forall(_._2.`type`.isInstanceOf[OpenapiSchemaSimpleType]) =>
            inlineDefn(endpointName, position, schemaRef)
          case x => bail(s"Can't create non-simple or array params as output (found $x)")
        }
        val req = if (required) outT else s"Option[$outT]"
        MappedContentType(s"jsonBody[$req]" + v(required), req, maybeInline, maybeInline.map(_ => outT).toSeq)

      case "multipart/form-data" =>
        schema match {
          case _: OpenapiSchemaBinary =>
            MappedContentType("multipartBody", "Seq[Part[Array[Byte]]]")
          case schemaRef: OpenapiSchemaRef =>
            val (t, _) = mapSchemaSimpleTypeToType(schemaRef, multipartForm = true)
            MappedContentType(s"multipartBody[$t]" + v(required), t)
          case schemaRef: OpenapiSchemaObject if schemaRef.properties.forall(_._2.`type`.isInstanceOf[OpenapiSchemaSimpleType]) =>
            val (inlineClassName, inlineClassDefn) = inlineDefn(endpointName, position, schemaRef)
            MappedContentType(
              s"multipartBody[$inlineClassName]" + v(required),
              inlineClassName,
              inlineClassDefn,
              inlineClassDefn.map(_ => inlineClassName).toSeq
            )
          case x => bail(s"$contentType only supports schema ref or binary, or simple inline property maps with string values. Found $x")
        }
      case other =>
        failoverBinaryCase(
          endpointName,
          position,
          other,
          schema,
          eager,
          streamingImplementation
        )
    }
  }

  private def failoverBinaryCase(
      endpointName: String,
      position: Position,
      contentType: String,
      schema: OpenapiSchemaType,
      isEager: Boolean,
      streamingImplementation: StreamingImplementation
  )(implicit location: Location): MappedContentType = {
    def codec(baseType: String, contentType: String) = {
      val cf = codecFormatName(contentType)
      val schema = if (baseType == "Array[Byte]") "Schema.schemaForByteArray" else "Schema.schemaForString"
      s"Codec.id[$baseType, $cf]($cf(), $schema)"
    }

    def eagerBody = contentType match {
      case "application/octet-stream" => "rawBinaryBody(sttp.tapir.RawBodyType.ByteArrayBody)"
      case o if o.startsWith("text/") => s"stringBodyUtf8AnyFormat(${codec("String", o)})"
      case "application/xml"          => s"EndpointIO.Body(RawBodyType.ByteArrayBody, CodecFormat.Xml(), EndpointIO.Info.empty)"
      case o                          => s"EndpointIO.Body(RawBodyType.ByteArrayBody, ${codec("Array[Byte]", o)}, EndpointIO.Info.empty)"
    }
    def streamingBody = contentType match {
      case "text/plain"                        => "CodecFormat.TextPlain()"
      case "text/html"                         => "CodecFormat.TextHtml()"
      case "multipart/form-data"               => "CodecFormat.MultipartFormData()"
      case "application/grpc"                  => "CodecFormat.Grpc()"
      case "application/json"                  => "CodecFormat.Json()"
      case "application/octet-stream"          => "CodecFormat.OctetStream()"
      case "application/xml"                   => "CodecFormat.Xml()"
      case "application/x-www-form-urlencoded" => "CodecFormat.XWwwFormUrlencoded()"
      case "application/zip"                   => "CodecFormat.Zip()"
      case o                                   => s"${codecFormatName(o)}()"
    }
    if (isEager) MappedContentType(eagerBody, if (contentType.startsWith("text/")) "String" else "Array[Byte]")
    else {
      val capability = capabilityImpl(streamingImplementation)
      val tpe = capabilityType(streamingImplementation)
      schema match {
        case _: OpenapiSchemaString =>
          MappedContentType(s"streamTextBody($capability)($streamingBody)", tpe)
        case schema =>
          val (outT, maybeInlineDefn) = schema match {
            case st: OpenapiSchemaSimpleType =>
              val (t, _) = mapSchemaSimpleTypeToType(st)
              t -> None
            case OpenapiSchemaArray(st: OpenapiSchemaSimpleType, _, _, _) =>
              val (t, _) = mapSchemaSimpleTypeToType(st)
              s"List[$t]" -> None
            case OpenapiSchemaMap(st: OpenapiSchemaSimpleType, _, _) =>
              val (t, _) = mapSchemaSimpleTypeToType(st)
              s"Map[String, $t]" -> None
            case o: OpenapiSchemaObject => inlineDefn(endpointName, position, o)
            case x                      => bail(s"Can't create this param as output (found $x)")
          }
          MappedContentType(
            s"streamBody($capability)(Schema.binary[$outT], $streamingBody)",
            tpe,
            inlineDefns = maybeInlineDefn,
            inlineTypes = maybeInlineDefn.map(_ => outT).toSeq
          )
      }
    }
  }

  private def inlineDefn(endpointName: String, position: Position, schemaRef: OpenapiSchemaObject) = {
    require(schemaRef.properties.forall(_._2.`type`.isInstanceOf[OpenapiSchemaSimpleType]))
    val inlineClassName = endpointName.capitalize + position
    val properties = schemaRef.properties.map { case (k, v) =>
      val (st, nb) = mapSchemaSimpleTypeToType(v.`type`.asInstanceOf[OpenapiSchemaSimpleType], multipartForm = true)
      val optional = !schemaRef.required.contains(k) || nb
      val t = if (optional) s"Option[$st]" else st
      val default = v.default
        .map(j => " = " + DefaultValueRenderer.render(Map.empty, v.`type`, optional, RenderConfig())(j))
        .getOrElse(if (optional) " = None" else "")
      // `k` is an inline-body property name straight from the (untrusted) document and is not covered by the
      // component-schema NameValidation pass, so quote/reject it here as the component-class path does. GHSA-gpcc-36pq-8qxr
      s"${safeVariableName(k)}: $t$default"
    }
    val inlineClassDefn =
      s"""case class $inlineClassName (
         |${indent(2)(properties.mkString(",\n"))}
         |)""".stripMargin
    inlineClassName -> Some(inlineClassDefn)
  }
}
