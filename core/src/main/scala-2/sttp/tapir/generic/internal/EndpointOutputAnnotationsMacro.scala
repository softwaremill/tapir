package sttp.tapir.generic.internal

import sttp.model.StatusCode
import sttp.model.headers.{CookieValueWithMeta, CookieWithMeta}
import sttp.tapir.EndpointOutput
import sttp.tapir.EndpointIO.annotations._
import sttp.tapir.internal.CaseClassUtil

import scala.collection.mutable
import scala.reflect.macros.blackbox

class EndpointOutputAnnotationsMacro(override val c: blackbox.Context) extends EndpointAnnotationsMacro(c) {
  import c.universe._

  private val setCookieType = c.weakTypeOf[setCookie]
  private val setCookiesType = c.weakTypeOf[setCookies]
  private val statusCodeType = c.weakTypeOf[statusCode]

  def generateEndpointOutput[A: c.WeakTypeTag]: c.Expr[EndpointOutput[A]] = {
    val util = new CaseClassUtil[c.type, A](c, "response endpoint")
    validateCaseClass(util)
    if (util.fields.isEmpty) {
      c.abort(c.enclosingPosition, "Case class must have at least one field")
    }
    val outputs = util.fields map { field =>
      val output = util
        .extractOptStringArgFromAnnotation(field, headerType)
        .map(makeHeaderIO(field))
        .orElse(util.extractOptStringArgFromAnnotation(field, setCookieType).map(makeSetCookieOutput(field)))
        .orElse(bodyAnnotation(field).map(makeBodyIO(field)))
        .orElse(if (util.annotated(field, statusCodeType)) Some(makeStatusCodeOutput(field)) else None)
        .orElse(if (util.annotated(field, headersType)) Some(makeHeadersIO(field)) else None)
        .orElse(if (util.annotated(field, cookiesType)) Some(makeCookiesIO(field)) else None)
        .orElse(if (util.annotated(field, setCookiesType)) Some(makeSetCookiesOutput(field)) else None)
        .getOrElse {
          c.abort(
            c.enclosingPosition,
            "All fields in case class must be marked with response annotation from package sttp.tapir.annotations"
          )
        }

      addMetadataFromAnnotations(output, field, util)
    }

    val result = outputs.reduceLeft { (left, right) => q"$left.and($right)" }
    val fieldIdxToInputIdx = mutable.Map((0 until outputs.size).map(i => (i, i)): _*)
    val mapperTo = mapToTargetFunc(fieldIdxToInputIdx, util)
    val mapperFrom = mapFromTargetFunc(fieldIdxToInputIdx, util)
    c.Expr[EndpointOutput[A]](q"$result.map($mapperTo)($mapperFrom)")
  }

  private def makeSetCookieOutput(field: c.Symbol)(altName: Option[String]): Tree =
    if (field.info.resultType =:= typeOf[CookieValueWithMeta]) {
      val name = altName.getOrElse(field.name.toTermName.decodedName.toString)
      q"_root_.sttp.tapir.setCookie($name)"
    } else {
      c.abort(c.enclosingPosition, s"Annotation @setCookie can be applied only for field with type ${typeOf[CookieValueWithMeta]}")
    }

  private def makeSetCookiesOutput(field: c.Symbol): Tree =
    if (field.info.resultType =:= typeOf[List[CookieWithMeta]]) {
      q"_root_.sttp.tapir.setCookies"
    } else {
      c.abort(c.enclosingPosition, s"Annotation @setCookies can be applied only for field with type ${typeOf[List[CookieWithMeta]]}")
    }

  private def makeStatusCodeOutput(field: c.Symbol): Tree =
    if (field.info.resultType =:= typeOf[StatusCode]) {
      q"_root_.sttp.tapir.statusCode"
    } else {
      c.abort(c.enclosingPosition, s"Annotation @statusCode can be applied only for field with type ${typeOf[StatusCode]}")
    }
}
