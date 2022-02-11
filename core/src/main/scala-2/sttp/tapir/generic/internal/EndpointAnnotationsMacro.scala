package sttp.tapir.generic.internal

import sttp.model.Header
import sttp.model.headers.Cookie
import sttp.tapir.EndpointIO.annotations._
import sttp.tapir.{Codec, MultipartCodec, RawPart, Schema}
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.internal.CaseClassUtil

import scala.collection.mutable
import scala.reflect.macros.blackbox

abstract class EndpointAnnotationsMacro(val c: blackbox.Context) {
  import c.universe._

  protected val headerType = c.weakTypeOf[header]
  protected val headersType = c.weakTypeOf[headers]
  protected val cookiesType = c.weakTypeOf[cookies]

  private val schemaDescriptionType = c.weakTypeOf[Schema.annotations.description]
  private val schemaEncodedExampleType = c.weakTypeOf[Schema.annotations.encodedExample]
  private val schemaDefaultType = c.weakTypeOf[Schema.annotations.default[_]]
  private val schemaFormatType = c.weakTypeOf[Schema.annotations.format]
  private val schemaDeprecatedType = c.weakTypeOf[Schema.annotations.deprecated]
  private val schemaValidateType = c.weakTypeOf[Schema.annotations.validate[_]]

  private val descriptionType = c.weakTypeOf[description]
  private val exampleType = c.weakTypeOf[example]

  private val apikeyType = c.weakTypeOf[apikey]
  protected val securitySchemeNameType = c.weakTypeOf[securitySchemeName]

  protected def validateCaseClass[A](util: CaseClassUtil[c.type, A]): Unit = {
    if (util.fields.isEmpty) {
      c.abort(c.enclosingPosition, "Case class must have at least one field")
    }
    if (1 < util.fields.flatMap(bodyAnnotation).size) {
      c.abort(c.enclosingPosition, "No more than one body annotation is allowed")
    }
  }

  type StringListCodec[T] = Codec[List[String], T, TextPlain]
  val stringListConstructor: c.universe.Type = typeOf[StringListCodec[_]].typeConstructor

  protected def makeHeaderIO(field: c.Symbol)(altName: Option[String]): Tree = {
    val name = altName.getOrElse(field.name.toTermName.decodedName.toString)
    q"_root_.sttp.tapir.header[${field.asTerm.info}]($name)"
  }

  protected def makeHeadersIO(field: c.Symbol): Tree =
    if (field.info.resultType =:= typeOf[List[Header]]) {
      q"_root_.sttp.tapir.headers"
    } else {
      c.abort(c.enclosingPosition, s"Annotation @headers can be applied only for field with type ${typeOf[List[Header]]}")
    }

  protected def makeCookiesIO(field: c.Symbol): Tree =
    if (field.info.resultType =:= typeOf[List[Cookie]]) {
      q"_root_.sttp.tapir.cookies"
    } else {
      c.abort(c.enclosingPosition, s"Annotation @cookies can be applied only for field with type ${typeOf[List[Cookie]]}")
    }

  protected def summonCodec(field: c.Symbol, tpe: Type): Tree = {
    val codecTpe = appliedType(tpe, field.asTerm.info)
    val codec = c.inferImplicitValue(codecTpe, silent = true)
    if (codec == EmptyTree) {
      c.abort(c.enclosingPosition, s"Unable to resolve implicit value of type ${codecTpe.dealias}")
    }
    codec
  }

  protected def bodyAnnotation(field: c.Symbol): Option[c.universe.Annotation] =
    field.annotations.find(_.tree.tpe <:< typeOf[body[_, _]])

  protected def makeBodyIO(field: c.Symbol)(ann: c.universe.Annotation): Tree = {
    val annTpe = ann.tree.tpe
    val bodyTypeType = annTpe.member(TermName("bodyType")).infoIn(annTpe).finalResultType
    val rawType = bodyTypeType.typeArgs.head
    val resultType = field.asTerm.info
    if (rawType =:= typeOf[Seq[RawPart]]) { // multipart body
      val codecTpe = appliedType(typeOf[MultipartCodec[_]], resultType)
      val codec = c.inferImplicitValue(codecTpe, silent = true)
      if (codec == EmptyTree) {
        c.abort(c.enclosingPosition, s"Unable to resolve implicit value of type ${codecTpe.dealias}")
      }
      q"_root_.sttp.tapir.EndpointIO.Body(${codec}.rawBodyType, ${codec}.codec, _root_.sttp.tapir.EndpointIO.Info.empty)"
    } else {
      val codecFormatType = annTpe.member(TermName("cf")).infoIn(annTpe).finalResultType
      val codecTpe = appliedType(typeOf[Codec[_, _, _]], rawType, resultType, codecFormatType)
      val codec = c.inferImplicitValue(codecTpe, silent = true)
      if (codec == EmptyTree) {
        c.abort(c.enclosingPosition, s"Unable to resolve implicit value of type ${codecTpe.dealias}")
      }
      q"_root_.sttp.tapir.EndpointIO.Body(${c.untypecheck(ann.tree)}.bodyType, $codec, _root_.sttp.tapir.EndpointIO.Info.empty)"
    }
  }

  protected def mapToTargetFunc[A](inputIdxToFieldIdx: mutable.Map[Int, Int], util: CaseClassUtil[c.type, A]): Tree = {
    val className = util.className
    if (inputIdxToFieldIdx.size > 1) {
      val tupleTypeComponents = (0 until inputIdxToFieldIdx.size) map { idx =>
        val field = util.fields(inputIdxToFieldIdx(idx))
        q"${field.asTerm.info}"
      }

      val fieldIdxToInputIdx = inputIdxToFieldIdx.map(_.swap)

      val tupleType = tq"(..$tupleTypeComponents)"
      val ctorArgs = (0 until fieldIdxToInputIdx.size) map { idx =>
        val fieldName = TermName(s"_${fieldIdxToInputIdx(idx) + 1}")
        q"t.$fieldName"
      }

      q"(t: $tupleType) => $className(..$ctorArgs)"
    } else {
      q"(t: ${util.fields.head.info}) => $className(t)"
    }
  }

  protected def mapFromTargetFunc[A](inputIdxToFieldIdx: mutable.Map[Int, Int], util: CaseClassUtil[c.type, A]): Tree = {
    val tupleArgs = (0 until inputIdxToFieldIdx.size) map { idx =>
      val field = util.fields(inputIdxToFieldIdx(idx))
      val fieldName = TermName(s"${field.name}")
      q"t.$fieldName"
    }
    val classType = util.classSymbol.asType
    q"(t: $classType) => (..$tupleArgs)"
  }

  protected def addMetadataFromAnnotations[A](input: Tree, field: Symbol, util: CaseClassUtil[c.type, A]): Tree = {
    val transformations: List[Tree => Tree] = List(
      i => util.extractStringArgFromAnnotation(field, descriptionType).fold(i)(desc => q"$i.description($desc)"),
      i => util.extractStringArgFromAnnotation(field, schemaDescriptionType).fold(i)(desc => q"$i.schema(_.description($desc))"),
      i =>
        util
          .extractTreeFromAnnotation(field, exampleType)
          .fold(i)(example => q"$i.example($example)"),
      i =>
        util
          .extractTreeFromAnnotation(field, schemaEncodedExampleType)
          .fold(i)(encodedExample => q"$i.schema(_.encodedExample($encodedExample))"),
      i =>
        util
          .extractTreeAndOptFromAnnotation(field, schemaDefaultType)
          .fold(i)(default => q"$i.default(${default._1}, encoded=${default._2})"),
      i =>
        util
          .extractStringArgFromAnnotation(field, schemaFormatType)
          .fold(i)(format => q"$i.schema(_.format($format))"),
      i => if (util.annotated(field, schemaDeprecatedType)) q"$i.deprecated()" else i,
      i =>
        util
          .extractTreeFromAnnotation(field, schemaValidateType)
          .fold(i)(validator => q"$i.validate($validator)"),
      i =>
        util.findAnnotation(field, apikeyType).fold(i) { a =>
          val challenge = authChallenge(a)
          setSecuritySchemeName(
            q"_root_.sttp.tapir.EndpointInput.Auth($i, None, $challenge, _root_.sttp.tapir.EndpointInput.AuthInfo.ApiKey())",
            util.findAnnotation(field, securitySchemeNameType)
          )
        }
    )

    transformations.foldLeft(input)((i, f) => f(i))
  }

  protected def info(any: Any): Unit =
    c.info(c.enclosingPosition, any.toString, true)

  protected def authChallenge(annotation: Annotation): Tree = {
    q"${c.untypecheck(annotation.tree)}.challenge"
  }

  protected def setSecuritySchemeName[A](auth: Tree, schemeName: Option[Annotation]): Tree = {
    schemeName.fold(auth) { name =>
      q"${c.untypecheck(auth)}.securitySchemeName(${c.untypecheck(name.tree)}.name)"
    }
  }
}
