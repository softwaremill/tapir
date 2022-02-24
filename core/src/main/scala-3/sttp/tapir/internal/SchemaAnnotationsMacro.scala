package sttp.tapir.internal

import scala.quoted.*
import sttp.tapir.Schema.annotations.format

trait SchemaAnnotationsMacro {
  implicit inline def derived[T]: SchemaAnnotations[T] = ${
    SchemaAnnotationsMacroImpl.derived[T]
  }
}

object SchemaAnnotationsMacroImpl {
  def derived[T: Type](using q: Quotes): Expr[SchemaAnnotations[T]] = {
    import q.reflect.*

    val EnumerationValue = TypeTree.of[scala.Enumeration#Value].tpe
    val DescriptionAnn = TypeTree.of[sttp.tapir.Schema.annotations.description].tpe
    val EncodedExampleAnn = TypeTree.of[sttp.tapir.Schema.annotations.encodedExample].tpe
    val DefaultAnn = TypeTree.of[sttp.tapir.Schema.annotations.default[_]].tpe
    val FormatAnn = TypeTree.of[sttp.tapir.Schema.annotations.format].tpe
    val DeprecatedAnn = TypeTree.of[sttp.tapir.Schema.annotations.deprecated].tpe
    val EncodedNameAnn = TypeTree.of[sttp.tapir.Schema.annotations.encodedName].tpe
    val ValidateAnn = TypeTree.of[sttp.tapir.Schema.annotations.validate[_]].tpe

    val tpe = TypeRepr.of[T]

    // if derivation is for Enumeration.Value then we lookup annotations on parent object that extend Enumeration
    val annotations = if (tpe <:< EnumerationValue) {
      val enumerationPath = tpe.show.split("\\.").dropRight(1).mkString(".")
      Symbol.requiredModule(enumerationPath).annotations
    } else tpe.typeSymbol.annotations

    def firstAnnArg(tpe: TypeRepr): Option[Tree] = {
      annotations
        .collectFirst {
          case ann if ann.tpe <:< tpe =>
            ann match {
              case Apply(_, List(tree)) => tree
            }
        }
    }

    val transformations: List[Expr[SchemaAnnotations[T]] => Expr[SchemaAnnotations[T]]] =
      List(
        sa => firstAnnArg(DescriptionAnn).map(arg => '{ ${ sa }.copy(description = Some(${ arg.asExprOf[String] })) }).getOrElse(sa),
        sa => firstAnnArg(EncodedExampleAnn).map(arg => '{ ${ sa }.copy(encodedExample = Some(${ arg.asExprOf[Any] })) }).getOrElse(sa),
        sa => firstAnnArg(DefaultAnn).map(arg => '{ ${ sa }.copy(default = Some(${ arg.asExprOf[T] })) }).getOrElse(sa),
        sa => firstAnnArg(FormatAnn).map(arg => '{ ${ sa }.copy(format = Some(${ arg.asExprOf[String] })) }).getOrElse(sa),
        sa => annotations.find { _.tpe <:< DeprecatedAnn }.map(_ => '{ ${ sa }.copy(deprecated = Some(true)) }).getOrElse(sa),
        sa => firstAnnArg(EncodedNameAnn).map(arg => '{ ${ sa }.copy(encodedName = Some(${ arg.asExprOf[String] })) }).getOrElse(sa),
        sa =>
          firstAnnArg(ValidateAnn).map(arg => '{ ${ sa }.copy(validate = Some(${ arg.asExprOf[sttp.tapir.Validator[T]] })) }).getOrElse(sa)
      )

    transformations.foldLeft('{ SchemaAnnotations[T](None, None, None, None, None, None, None) })((sa, t) => t(sa))
  }
}
