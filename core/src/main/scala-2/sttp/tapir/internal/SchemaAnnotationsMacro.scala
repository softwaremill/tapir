package sttp.tapir.internal

import sttp.tapir.SchemaAnnotations

import scala.reflect.macros.blackbox

private[tapir] object SchemaAnnotationsMacro {
  def derived[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[SchemaAnnotations[T]] = {
    import c.universe._

    val EnumerationValue = typeOf[scala.Enumeration#Value]
    val DescriptionAnn = typeOf[sttp.tapir.Schema.annotations.description]
    val EncodedExampleAnn = typeOf[sttp.tapir.Schema.annotations.encodedExample]
    val DefaultAnn = typeOf[sttp.tapir.Schema.annotations.default[_]]
    val FormatAnn = typeOf[sttp.tapir.Schema.annotations.format]
    val DeprecatedAnn = typeOf[sttp.tapir.Schema.annotations.deprecated]
    val HiddenAnn = typeOf[sttp.tapir.Schema.annotations.hidden]
    val EncodedNameAnn = typeOf[sttp.tapir.Schema.annotations.encodedName]
    val ValidateAnn = typeOf[sttp.tapir.Schema.annotations.validate[_]]
    val ValidateEachAnn = typeOf[sttp.tapir.Schema.annotations.validateEach[_]]

    val weakType = weakTypeOf[T]

    // if derivation is for Enumeration.Value then we lookup annotations on parent object that extend Enumeration
    val annotations = if (weakType <:< EnumerationValue) {
      weakTypeTag[T].tpe match {
        case t @ TypeRef(_, _, _) => t.pre.typeSymbol.annotations
        case _                    => c.abort(c.enclosingPosition, s"Cannot extract TypeRef from type ${weakType.typeSymbol.fullName}")
      }
    } else weakType.typeSymbol.annotations

    val firstArg: Annotation => Tree = a => a.tree.children.tail.head
    val firstTwoArgs: Annotation => (Tree, Tree) = a => (a.tree.children.tail.head, a.tree.children.tail(1))

    val description = annotations.collectFirst { case ann if ann.tree.tpe <:< DescriptionAnn => firstArg(ann) }
    val encodedExample = annotations.collectFirst { case ann if ann.tree.tpe <:< EncodedExampleAnn => firstArg(ann) }
    val default = annotations.collectFirst { case ann if ann.tree.tpe <:< DefaultAnn => firstTwoArgs(ann) }
    val format = annotations.collectFirst { case ann if ann.tree.tpe <:< FormatAnn => firstArg(ann) }
    val deprecated = annotations.collectFirst { case ann if ann.tree.tpe <:< DeprecatedAnn => q"""true""" }
    val hidden = annotations.collectFirst { case ann if ann.tree.tpe <:< HiddenAnn => q"""true""" }
    val encodedName = annotations.collectFirst { case ann if ann.tree.tpe <:< EncodedNameAnn => firstArg(ann) }
    val validate = annotations.collect { case ann if ann.tree.tpe <:< ValidateAnn => firstArg(ann) }
    val validateEach = annotations.collect { case ann if ann.tree.tpe <:< ValidateEachAnn => firstArg(ann) }

    c.Expr[SchemaAnnotations[T]](
      q"""_root_.sttp.tapir.SchemaAnnotations.apply($description, $encodedExample, $default, $format, $deprecated, $hidden, $encodedName, _root_.scala.List(..$validate), _root_.scala.List(..$validateEach))"""
    )
  }
}
