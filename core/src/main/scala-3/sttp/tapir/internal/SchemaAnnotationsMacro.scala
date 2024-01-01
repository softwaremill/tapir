package sttp.tapir.internal

import sttp.tapir.Schema.annotations.format
import sttp.tapir.SchemaAnnotations

import scala.quoted.*

private[tapir] object SchemaAnnotationsMacro {
  def derived[T: Type](using q: Quotes): Expr[SchemaAnnotations[T]] = {
    import q.reflect.*

    val EnumerationValue = TypeTree.of[scala.Enumeration#Value].tpe
    val DescriptionAnn = TypeTree.of[sttp.tapir.Schema.annotations.description].tpe
    val EncodedExampleAnn = TypeTree.of[sttp.tapir.Schema.annotations.encodedExample].tpe
    val DefaultAnn = TypeTree.of[sttp.tapir.Schema.annotations.default[_]].tpe
    val FormatAnn = TypeTree.of[sttp.tapir.Schema.annotations.format].tpe
    val DeprecatedAnn = TypeTree.of[sttp.tapir.Schema.annotations.deprecated].tpe
    val HiddenAnn = TypeTree.of[sttp.tapir.Schema.annotations.hidden].tpe
    val EncodedNameAnn = TypeTree.of[sttp.tapir.Schema.annotations.encodedName].tpe
    val ValidateAnn = TypeTree.of[sttp.tapir.Schema.annotations.validate[_]].tpe
    val ValidateEachAnn = TypeTree.of[sttp.tapir.Schema.annotations.validateEach[_]].tpe

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

    def allAnnArg(tpe: TypeRepr): List[Tree] = {
      annotations
        .collect {
          case ann if ann.tpe <:< tpe =>
            ann match {
              case Apply(_, List(tree)) => tree
            }
        }
    }

    def firstTwoAnnArgs(tpe: TypeRepr): Option[(Tree, Tree)] = {
      annotations
        .collectFirst {
          case ann if ann.tpe <:< tpe =>
            ann match {
              case Apply(_, List(t1, t2)) => (t1, t2)
            }
        }
    }

    val transformations: List[Expr[SchemaAnnotations[T]] => Expr[SchemaAnnotations[T]]] =
      List(
        sa => firstAnnArg(DescriptionAnn).map(arg => '{ ${ sa }.copy(description = Some(${ arg.asExprOf[String] })) }).getOrElse(sa),
        sa => firstAnnArg(EncodedExampleAnn).map(arg => '{ ${ sa }.copy(encodedExample = Some(${ arg.asExprOf[Any] })) }).getOrElse(sa),
        sa =>
          firstTwoAnnArgs(DefaultAnn)
            .map(args => '{ ${ sa }.copy(default = Some((${ args._1.asExprOf[T] }, ${ args._2.asExprOf[Option[Any]] }))) })
            .getOrElse(sa),
        sa => firstAnnArg(FormatAnn).map(arg => '{ ${ sa }.copy(format = Some(${ arg.asExprOf[String] })) }).getOrElse(sa),
        sa => annotations.find { _.tpe <:< DeprecatedAnn }.map(_ => '{ ${ sa }.copy(deprecated = Some(true)) }).getOrElse(sa),
        sa => annotations.find { _.tpe <:< HiddenAnn }.map(_ => '{ ${ sa }.copy(hidden = Some(true)) }).getOrElse(sa),
        sa => firstAnnArg(EncodedNameAnn).map(arg => '{ ${ sa }.copy(encodedName = Some(${ arg.asExprOf[String] })) }).getOrElse(sa),
        sa => '{ ${ sa }.copy(validate = ${ Expr.ofList(allAnnArg(ValidateAnn).map(_.asExprOf[sttp.tapir.Validator[T]])) }) },
        sa => '{ ${ sa }.copy(validateEach = ${ Expr.ofList(allAnnArg(ValidateEachAnn).map(_.asExprOf[sttp.tapir.Validator[Any]])) }) }
      )

    transformations.foldLeft('{ SchemaAnnotations.empty[T] })((sa, t) => t(sa))
  }
}
