package sttp.tapir

import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.annotations
import sttp.tapir.internal.{AnnotationsMacros, CaseClass, CaseClassField}
import sttp.tapir.typelevel.ParamConcat
import sttp.model.{QueryParams, Header}
import sttp.model.headers.Cookie

import scala.collection.mutable
import scala.quoted.*
import scala.deriving.Mirror

package object annotations {
  inline def deriveEndpointInput[T <: Product]: EndpointInput[T] = ${AnnotationsMacrosDelegate.deriveEndpointInputImpl[T]}
  inline def deriveEndpointOutput[T <: Product]: EndpointOutput[T] = ${AnnotationsMacrosDelegate.deriveEndpointOutputImpl[T]}
}

object AnnotationsMacrosDelegate {
  def deriveEndpointInputImpl[T <: Product: Type](using q: Quotes): Expr[EndpointInput[T]] = new AnnotationsMacros[T].deriveEndpointInputImpl
  def deriveEndpointOutputImpl[T <: Product: Type](using q: Quotes): Expr[EndpointOutput[T]] = new AnnotationsMacros[T].deriveEndpointOutputImpl
}
