package sttp.tapir.internal

import scala.reflect.macros.blackbox

object SchemaAnnotationsMacro {
  def derived[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[SchemaAnnotations[T]] = {
    import c.universe._

    val DescriptionAnn = typeOf[sttp.tapir.Schema.annotations.description]
    val EncodedExampleAnn = typeOf[sttp.tapir.Schema.annotations.encodedExample]
    val DefaultAnn = typeOf[sttp.tapir.Schema.annotations.default[_]]
    val FormatAnn = typeOf[sttp.tapir.Schema.annotations.format]
    val DeprecatedAnn = typeOf[sttp.tapir.Schema.annotations.deprecated]
    val EncodedNameAnn = typeOf[sttp.tapir.Schema.annotations.encodedName]
    val ValidateAnn = typeOf[sttp.tapir.Schema.annotations.validate[_]]

    val annotations = weakTypeOf[T].typeSymbol.annotations

    val firstArg: Annotation => Tree = a => a.tree.children.tail.head

    val description = annotations.collectFirst { case ann if ann.tree.tpe <:< DescriptionAnn => firstArg(ann) }
    val encodedExample = annotations.collectFirst { case ann if ann.tree.tpe <:< EncodedExampleAnn => firstArg(ann) }
    val default = annotations.collectFirst { case ann if ann.tree.tpe <:< DefaultAnn => firstArg(ann) }
    val format = annotations.collectFirst { case ann if ann.tree.tpe <:< FormatAnn => firstArg(ann) }
    val deprecated = annotations.collectFirst { case ann if ann.tree.tpe <:< DeprecatedAnn => q"""true""" }
    val encodedName = annotations.collectFirst { case ann if ann.tree.tpe <:< EncodedNameAnn => firstArg(ann) }
    val validator = annotations.collectFirst { case ann if ann.tree.tpe <:< ValidateAnn => firstArg(ann) }

    c.Expr[SchemaAnnotations[T]](
      q"""_root_.sttp.tapir.internal.SchemaAnnotations.apply($description, $encodedExample, $default, $format, $deprecated, $encodedName, $validator)"""
    )
  }
}
