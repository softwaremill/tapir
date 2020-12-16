package sttp.tapir.generic.internal

import sttp.model._
import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.EndpointInput
import sttp.tapir.annotations._

import scala.collection.mutable
import scala.reflect.macros.blackbox

class EndpointInputAnnotations(override val c: blackbox.Context) extends EndpointAnnotations(c) {
  import c.universe._

  private val endpointInput = c.weakTypeOf[endpointInput]
  private val pathType = c.weakTypeOf[path]
  private val queryType = c.weakTypeOf[query]
  private val cookieType = c.weakTypeOf[cookie]
  private val paramsType = c.weakTypeOf[params]
  private val bearerType = c.weakTypeOf[bearer]
  private val basicType = c.weakTypeOf[basic]

  def deriveEndpointInput[A: c.WeakTypeTag]: c.Expr[EndpointInput[A]] = {
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
        .orElse(util.extractOptArgFromAnnotation(field, headerType).map(makeHeaderIO(field)))
        .orElse(util.extractOptArgFromAnnotation(field, cookieType).map(makeCookieInput(field)))
        .orElse(hasBodyAnnotation(field).map(makeBodyIO(field)))
        .orElse(if (util.annotated(field, paramsType)) Some(makeQueryParamsInput(field)) else None)
        .orElse(if (util.annotated(field, headersType)) Some(makeHeadersIO(field)) else None)
        .orElse(if (util.annotated(field, cookiesType)) Some(makeCookiesIO(field)) else None)
        .orElse(util.findAnnotation(field, bearerType).map(makeBearerAuthInput(field, _)))
        .orElse(util.findAnnotation(field, basicType).map(makeBasicAuthInput(field, _)))
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

  type StringOptionCodec[T] = Codec[Option[String], T, TextPlain]
  val stringOptionConstructor = typeOf[StringOptionCodec[_]].typeConstructor

  private def makeQueryInput(field: c.Symbol)(altName: Option[String]): Tree = {
    val name = altName.getOrElse(field.name.toTermName.decodedName.toString)
    q"query[${field.asTerm.info}]($name)"
  }

  private def makeCookieInput(field: c.Symbol)(altName: Option[String]): Tree = {
    val name = altName.getOrElse(field.name.toTermName.decodedName.toString)
    q"cookie[${field.asTerm.info}]($name)"
  }

  private def makePathInput(field: c.Symbol): Tree = {
    val name = field.name.toTermName.decodedName.toString
    q"path[${field.asTerm.info}]($name)"
  }

  private def makeFixedPath(segment: String): Tree =
    q"stringToPath($segment)"

  private def makeQueryParamsInput(field: c.Symbol): Tree =
    if (field.info.resultType =:= typeOf[QueryParams]) {
      q"queryParams"
    } else {
      c.abort(c.enclosingPosition, s"Annotation @params can be applied only for field with type ${typeOf[QueryParams]}")
    }

  private def makeBearerAuthInput(field: c.Symbol, annotation: Annotation): Tree = {
    val challenge = authChallenge(annotation)
    val codec = summonCodec(field, stringListConstructor)
    q"sttp.tapir.TapirAuth.bearer($challenge)($codec)"
  }

  private def makeBasicAuthInput(field: c.Symbol, annotation: Annotation): Tree = {
    val challenge = authChallenge(annotation)
    val codec = summonCodec(field, stringListConstructor)
    q"sttp.tapir.TapirAuth.basic($challenge)($codec)"
  }
}
