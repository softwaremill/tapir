package sttp.tapir.generic.internal

import sttp.model._
import sttp.tapir.EndpointInput
import sttp.tapir.EndpointIO.annotations._
import sttp.tapir.internal.CaseClassUtil

import scala.collection.mutable
import scala.reflect.macros.blackbox

private[tapir] class EndpointInputAnnotationsMacro(override val c: blackbox.Context) extends EndpointAnnotationsMacro(c) {
  import c.universe._

  private val endpointInput = c.weakTypeOf[endpointInput]
  private val pathType = c.weakTypeOf[path]
  private val queryType = c.weakTypeOf[query]
  private val cookieType = c.weakTypeOf[cookie]
  private val paramsType = c.weakTypeOf[params]
  private val bearerType = c.weakTypeOf[bearer]
  private val basicType = c.weakTypeOf[basic]

  def generateEndpointInput[A: c.WeakTypeTag]: c.Expr[EndpointInput[A]] = {
    val util = new CaseClassUtil[c.type, A](c, "request endpoint")
    validateCaseClass(util)

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
    val inputIdxToFieldIdx = mutable.Map.empty[Int, Int]

    val pathInputs = segments map { segment =>
      if (segment.startsWith("{") && segment.endsWith("}")) {
        val fieldName = segment.drop(1).dropRight(1)
        fieldsWithIndex.find({ case (symbol, _) =>
          util.annotated(symbol, pathType) && symbol.name.toTermName.decodedName.toString == fieldName
        }) match {
          case Some((symbol, idx)) =>
            inputIdxToFieldIdx += (inputIdxToFieldIdx.size -> idx)
            val input = makePathInput(symbol)
            addMetadataFromAnnotations(input, symbol, util)
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

    if (inputIdxToFieldIdx.size != pathFields.size) {
      c.abort(c.enclosingPosition, "Not all fields from target case class with annotation @path are captured in path")
    }

    val nonPathInputs = nonPathFields map { case (field, fieldIdx) =>
      val input = util
        .extractOptStringArgFromAnnotation(field, queryType)
        .map(makeQueryInput(field))
        .orElse(util.extractOptStringArgFromAnnotation(field, headerType).map(makeHeaderIO(field)))
        .orElse(util.extractOptStringArgFromAnnotation(field, cookieType).map(makeCookieInput(field)))
        .orElse(bodyAnnotation(field).map(makeBodyIO(field)))
        .orElse(if (util.annotated(field, paramsType)) Some(makeQueryParamsInput(field)) else None)
        .orElse(if (util.annotated(field, headersType)) Some(makeHeadersIO(field)) else None)
        .orElse(if (util.annotated(field, cookiesType)) Some(makeCookiesIO(field)) else None)
        .orElse(
          util.findAnnotation(field, bearerType).map(makeBearerAuthInput(field, util.findAnnotation(field, securitySchemeNameType), _))
        )
        .orElse(util.findAnnotation(field, basicType).map(makeBasicAuthInput(field, util.findAnnotation(field, securitySchemeNameType), _)))
        .getOrElse {
          c.abort(
            c.enclosingPosition,
            "All fields in case class must be marked with request annotation from package sttp.tapir.annotations"
          )
        }
      inputIdxToFieldIdx += (inputIdxToFieldIdx.size -> fieldIdx)
      addMetadataFromAnnotations(input, field, util)
    }

    val result = (pathInputs ::: nonPathInputs).reduceLeft { (left, right) => q"$left.and($right)" }
    val mapperTo = mapToTargetFunc(inputIdxToFieldIdx, util)
    val mapperFrom = mapFromTargetFunc(inputIdxToFieldIdx, util)

    c.Expr[EndpointInput[A]](q"$result.map($mapperTo)($mapperFrom)")
  }

  private def makeQueryInput(field: c.Symbol)(altName: Option[String]): Tree = {
    val name = altName.getOrElse(field.name.toTermName.decodedName.toString)
    q"_root_.sttp.tapir.query[${field.asTerm.info}]($name)"
  }

  private def makeCookieInput(field: c.Symbol)(altName: Option[String]): Tree = {
    val name = altName.getOrElse(field.name.toTermName.decodedName.toString)
    q"_root_.sttp.tapir.cookie[${field.asTerm.info}]($name)"
  }

  private def makePathInput(field: c.Symbol): Tree = {
    val name = field.name.toTermName.decodedName.toString
    q"_root_.sttp.tapir.path[${field.asTerm.info}]($name)"
  }

  private def makeFixedPath(segment: String): Tree =
    q"_root_.sttp.tapir.stringToPath($segment)"

  private def makeQueryParamsInput(field: c.Symbol): Tree =
    if (field.info.resultType =:= typeOf[QueryParams]) {
      q"_root_.sttp.tapir.queryParams"
    } else {
      c.abort(c.enclosingPosition, s"Annotation @params can be applied only for field with type ${typeOf[QueryParams]}")
    }

  private def makeBearerAuthInput(field: c.Symbol, schemeName: Option[Annotation], auth: Annotation): Tree = {
    val challenge = authChallenge(auth)
    val codec = summonCodec(field, stringListConstructor)
    setSecuritySchemeName(q"_root_.sttp.tapir.TapirAuth.bearer($challenge)($codec)", schemeName)
  }

  private def makeBasicAuthInput(field: c.Symbol, schemeName: Option[Annotation], annotation: Annotation): Tree = {
    val challenge = authChallenge(annotation)
    val codec = summonCodec(field, stringListConstructor)
    setSecuritySchemeName(q"_root_.sttp.tapir.TapirAuth.basic($challenge)($codec)", schemeName)
  }

}
