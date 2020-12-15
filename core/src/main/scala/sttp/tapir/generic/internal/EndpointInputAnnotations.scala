package sttp.tapir.generic.internal

import sttp.model._
import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.EndpointInput
import sttp.tapir.EndpointInput.WWWAuthenticate
import sttp.tapir.annotations._
import sttp.tapir.deprecated
import sttp.tapir.description

import scala.collection.mutable
import scala.reflect.macros.blackbox

class EndpointInputAnnotations(val c: blackbox.Context) {
  import c.universe._

  private val endpointInput = c.weakTypeOf[endpointInput]
  private val pathType = c.weakTypeOf[path]
  private val queryType = c.weakTypeOf[query]
  private val headerType = c.weakTypeOf[header]
  private val cookieType = c.weakTypeOf[cookie]
  private val paramsType = c.weakTypeOf[params]
  private val headersType = c.weakTypeOf[headers]
  private val cookiesType = c.weakTypeOf[cookies]
  private val bearerType = c.weakTypeOf[bearer]
  private val basicType = c.weakTypeOf[basic]
  private val descriptionType = c.weakTypeOf[description]
  private val deprecatedType = c.weakTypeOf[deprecated]
  private val apikeyType = c.weakTypeOf[apikey]

  def deriveEndpointInput[A: c.WeakTypeTag]: c.Expr[EndpointInput[A]] = {
    val util = new CaseClassUtil[c.type, A](c, "request endpoint")

    if (util.fields.isEmpty) {
      c.abort(c.enclosingPosition, "Case class must have at least one field")
    }
    if (1 < util.fields.flatMap(hasBodyAnnotation).size) {
      c.abort(c.enclosingPosition, "No more than one body annotation is allowed")
    }

    val segments = util.classSymbol.annotations
      .map(_.tree)
      .collectFirst { case Apply(Select(New(tree), _), List(arg)) if tree.tpe <:< endpointInput => arg }
      .collectFirst { case Literal(Constant(path: String)) =>
        val pathWithoutLeadingSlash = if (path.startsWith("/")) path.drop(1) else path
        val result = if (pathWithoutLeadingSlash.endsWith("/")) pathWithoutLeadingSlash.dropRight(1) else pathWithoutLeadingSlash
        if (result.length == 0) Nil else result.split("/").toList
      }
      .getOrElse(Nil)

    val fieldsWithIndex = util.fields.zipWithIndex
    val fieldIdxToInputIdx = mutable.Map.empty[Int, Int]

    val pathInputs = segments map { segment =>
      if (segment.startsWith("{") && segment.endsWith("}")) {
        val fieldName = segment.drop(1).dropRight(1)
        fieldsWithIndex.find({ case (symbol, _) =>
          util.annotated(symbol, pathType) && symbol.name.toTermName.decodedName.toString == fieldName
        }) match {
          case Some((symbol, idx)) =>
            fieldIdxToInputIdx += (fieldIdxToInputIdx.size -> idx)
            val input = makePathInput(symbol)
            assignSchemaAnnotations(input, symbol, util)
          case None =>
            c.abort(c.enclosingPosition, s"Target case class must have field with name $fieldName and annotation @path")
        }
      } else {
        makeFixedPath(segment)
      }
    }

    val (pathFields, nonPathFields) = fieldsWithIndex.partition { case (s, _) =>
      util.annotated(s, pathType)
    }

    if (fieldIdxToInputIdx.size != pathFields.size) {
      c.abort(c.enclosingPosition, "Not all fields from target case class with annotation @path are captured in path")
    }

    val nonPathInputs = nonPathFields map { case (field, fieldIdx) =>
      val input = util
        .extractOptArgFromAnnotation(field, queryType)
        .map(makeQueryInput(field))
        .orElse(util.extractOptArgFromAnnotation(field, headerType).map(makeHeaderInput(field)))
        .orElse(util.extractOptArgFromAnnotation(field, cookieType).map(makeCookieInput(field)))
        .orElse(hasBodyAnnotation(field).map(makeBodyInput(field)))
        .orElse(if (util.annotated(field, paramsType)) Some(makeQueryParamsInput(field)) else None)
        .orElse(if (util.annotated(field, headersType)) Some(makeHeadersInput(field)) else None)
        .orElse(if (util.annotated(field, cookiesType)) Some(makeCookiesInput(field)) else None)
        .orElse(util.findAnnotation(field, bearerType).map(makeBearerInput(field, _)))
        .orElse(util.findAnnotation(field, basicType).map(makeBasicInput(field, _)))
        .getOrElse {
          c.abort(
            c.enclosingPosition,
            "All fields in case class must be marked with request annotation from package sttp.tapir.annotations"
          )
        }
      fieldIdxToInputIdx += (fieldIdxToInputIdx.size -> fieldIdx)
      assignSchemaAnnotations(input, field, util)
    }

    val result = (pathInputs ::: nonPathInputs).reduceLeft { (left, right) => q"$left.and($right)" }
    val mapper = mapToTargetFunc(fieldIdxToInputIdx, util)
    c.Expr[EndpointInput[A]](q"$result.mapTo($mapper)")
  }

  type StringCodec[T] = Codec[String, T, TextPlain]
  val stringConstructor = typeOf[StringCodec[_]].typeConstructor

  type StringListCodec[T] = Codec[List[String], T, TextPlain]
  val stringListConstructor = typeOf[StringListCodec[_]].typeConstructor

  type StringOptionCodec[T] = Codec[Option[String], T, TextPlain]
  val stringOptionConstructor = typeOf[StringOptionCodec[_]].typeConstructor

  private def makeQueryInput(field: c.Symbol)(altName: Option[String]): Tree = {
    val codec = summonCodec(field, stringListConstructor)
    val name = altName.getOrElse(field.name.toTermName.decodedName.toString)
    q"sttp.tapir.EndpointInput.Query($name, $codec, sttp.tapir.EndpointIO.Info.empty)"
  }

  private def makeHeaderInput(field: c.Symbol)(altName: Option[String]): Tree = {
    val codec = summonCodec(field, stringListConstructor)
    val name = altName.getOrElse(field.name.toTermName.decodedName.toString)
    q"sttp.tapir.EndpointIO.Header($name, $codec, sttp.tapir.EndpointIO.Info.empty)"
  }

  private def makeCookieInput(field: c.Symbol)(altName: Option[String]): Tree = {
    val codec = summonCodec(field, stringOptionConstructor)
    val name = altName.getOrElse(field.name.toTermName.decodedName.toString)
    q"sttp.tapir.EndpointInput.Cookie($name, $codec, sttp.tapir.EndpointIO.Info.empty)"
  }

  private def makePathInput(field: c.Symbol): Tree = {
    val codec = summonCodec(field, stringConstructor)
    val name = field.name.toTermName.decodedName.toString
    q"sttp.tapir.EndpointInput.PathCapture(Some($name), $codec, sttp.tapir.EndpointIO.Info.empty)"
  }

  private def makeFixedPath(segment: String): Tree =
    q"sttp.tapir.EndpointInput.FixedPath($segment, sttp.tapir.Codec.idPlain(), sttp.tapir.EndpointIO.Info.empty)"

  private def makeQueryParamsInput(field: c.Symbol): Tree =
    if (field.info.resultType =:= typeOf[QueryParams]) {
      q"sttp.tapir.EndpointInput.QueryParams(sttp.tapir.Codec.idPlain(), sttp.tapir.EndpointIO.Info.empty)"
    } else {
      c.abort(c.enclosingPosition, s"Annotation @params can be applied only for field with type ${typeOf[QueryParams]}")
    }

  private def makeHeadersInput(field: c.Symbol): Tree =
    if (field.info.resultType =:= typeOf[List[Header]]) {
      q"sttp.tapir.EndpointIO.Headers(sttp.tapir.Codec.idPlain(), sttp.tapir.EndpointIO.Info.empty)"
    } else {
      c.abort(c.enclosingPosition, s"Annotation @headers can be applied only for field with type ${typeOf[List[Header]]}")
    }

  private def makeCookiesInput(field: c.Symbol): Tree =
    if (field.info.resultType =:= typeOf[List[Cookie]]) {
      val cookieHeader = HeaderNames.Cookie
      val codec = summonCodec(field, stringListConstructor)
      q"sttp.tapir.EndpointIO.Header($cookieHeader, $codec, sttp.tapir.EndpointIO.Info.empty)"
    } else {
      c.abort(c.enclosingPosition, s"Annotation @cookies can be applied only for field with type ${typeOf[List[Cookie]]}")
    }

  private def makeBearerInput(field: c.Symbol, annotation: Annotation): Tree = {
    val challenge = authChallenge(annotation)
    val codec = summonCodec(field, stringListConstructor)
    q"sttp.tapir.TapirAuth.bearer($challenge)($codec)"
  }

  private def makeBasicInput(field: c.Symbol, annotation: Annotation): Tree = {
    val challenge = authChallenge(annotation)
    val codec = summonCodec(field, stringListConstructor)
    q"sttp.tapir.TapirAuth.basic($challenge)($codec)"
  }

  private def summonCodec(field: c.Symbol, tpe: Type): Tree = {
    val codecTpe = appliedType(tpe, field.asTerm.info)
    val codec = c.inferImplicitValue(codecTpe, silent = true)
    if (codec == EmptyTree) {
      c.abort(c.enclosingPosition, s"Unable to resolve implicit value of type ${codecTpe.dealias}")
    }
    codec
  }

  private def hasBodyAnnotation(field: c.Symbol): Option[c.universe.Annotation] =
    field.annotations.find(_.tree.tpe <:< typeOf[body[_, _]])

  private def makeBodyInput(field: c.Symbol)(ann: c.universe.Annotation): Tree = {
    val annTpe = ann.tree.tpe
    val codecFormatType = annTpe.member(TermName("cf")).infoIn(annTpe).finalResultType
    val bodyTypeType = annTpe.member(TermName("bodyType")).infoIn(annTpe).finalResultType
    val rawType = bodyTypeType.typeArgs.head
    val resultType = field.asTerm.info
    val codecTpe = appliedType(typeOf[Codec[_, _, _]], rawType, resultType, codecFormatType)
    val codec = c.inferImplicitValue(codecTpe, silent = true)
    if (codec == EmptyTree) {
      c.abort(c.enclosingPosition, s"Unable to resolve implicit value of type ${codecTpe.dealias}")
    }
    q"sttp.tapir.EndpointIO.Body(${c.untypecheck(ann.tree)}.bodyType, $codec, sttp.tapir.EndpointIO.Info.empty)"
  }

  private def assignSchemaAnnotations[A](input: Tree, field: Symbol, util: CaseClassUtil[c.type, A]): Tree = {
    val inputWithDescription = util
      .extractArgFromAnnotation(field, descriptionType)
      .fold(input)(desc => q"$input.description($desc)")
    val inputWithDeprecation = if (util.annotated(field, deprecatedType)) {
      q"$inputWithDescription.deprecated"
    } else {
      inputWithDescription
    }
    util.findAnnotation(field, apikeyType).fold(inputWithDeprecation) { a =>
      val challenge = authChallenge(a)
      q"sttp.tapir.EndpointInput.Auth.ApiKey($inputWithDeprecation, $challenge)"
    }
  }

  private def authChallenge(annotation: Annotation): Tree = {
    q"${c.untypecheck(annotation.tree)}.challenge"
  }

  private def mapToTargetFunc[A](fieldIdxToInputIdx: mutable.Map[Int, Int], util: CaseClassUtil[c.type, A]) = {
    val funArgs = (0 until fieldIdxToInputIdx.size) map { idx =>
      val name = TermName(s"arg$idx")
      val field = util.fields(fieldIdxToInputIdx(idx))
      q"val $name: ${field.asTerm.info}"
    }
    val inputIdxToFieldIdx = fieldIdxToInputIdx.map(_.swap)
    val ctorArgs = (0 until inputIdxToFieldIdx.size) map { idx =>
      TermName(s"arg${inputIdxToFieldIdx(idx)}")
    }
    val className = util.classSymbol.asType.name.toTermName
    q"(..$funArgs) => $className(..$ctorArgs)"
  }

  def info(any: Any): Unit =
    c.info(c.enclosingPosition, any.toString, true)
}
