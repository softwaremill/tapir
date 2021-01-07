package sttp.tapir.generic.internal

import sttp.model.StatusCode
import sttp.model.headers.{CookieValueWithMeta, CookieWithMeta}
import sttp.tapir.EndpointOutput
import sttp.tapir.annotations.setCookie
import sttp.tapir.annotations.setCookies
import sttp.tapir.annotations.statusCode

import scala.collection.mutable
import scala.reflect.macros.blackbox

class EndpointOutputAnnotations(override val c: blackbox.Context) extends EndpointAnnotations(c) {
  import c.universe._

  private val setCookieType = c.weakTypeOf[setCookie]
  private val setCookiesType = c.weakTypeOf[setCookies]
  private val statusCodeType = c.weakTypeOf[statusCode]

  def deriveEndpointOutput[A: c.WeakTypeTag]: c.Expr[EndpointOutput[A]] = {
    val util = new CaseClassUtil[c.type, A](c, "response endpoint")
    validateCaseClass(util)

    val outputs = util.fields map { field =>
      val output = util
        .extractOptArgFromAnnotation(field, headerType)
        .map(makeHeaderIO(field))
        .orElse(util.extractOptArgFromAnnotation(field, setCookieType).map(makeSetCookieOutput(field)))
        .orElse(hasBodyAnnotation(field).map(makeBodyIO(field)))
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

      assignSchemaAnnotations(output, field, util)
    }

    val result = outputs.reduceLeft { (left, right) => q"$left.and($right)" }
    val mapper = mapToTargetFunc(mutable.Map((0 until outputs.size).map(i => (i, i)): _*), util)
    c.Expr[EndpointOutput[A]](q"$result.mapTo($mapper)")
  }

  def makeSetCookieOutput(field: c.Symbol)(altName: Option[String]): Tree =
    if (field.info.resultType =:= typeOf[CookieValueWithMeta]) {
      val name = altName.getOrElse(field.name.toTermName.decodedName.toString)
      q"setCookie($name)"
    } else {
      c.abort(c.enclosingPosition, s"Annotation @setCookie can be applied only for field with type ${typeOf[CookieValueWithMeta]}")
    }

  def makeSetCookiesOutput(field: c.Symbol): Tree =
    if (field.info.resultType =:= typeOf[List[CookieWithMeta]]) {
      q"setCookies"
    } else {
      c.abort(c.enclosingPosition, s"Annotation @setCookies can be applied only for field with type ${typeOf[List[CookieWithMeta]]}")
    }

  def makeStatusCodeOutput(field: c.Symbol): Tree =
    if (field.info.resultType =:= typeOf[StatusCode]) {
      q"statusCode"
    } else {
      c.abort(c.enclosingPosition, s"Annotation @statusCode can be applied only for field with type ${typeOf[StatusCode]}")
    }
}
