package sttp.tapir.internal

import scala.quoted.*

private[tapir] class CaseClass[Q <: Quotes, T: Type](using val q: Q) {
  import q.reflect.*

  val tpe = TypeRepr.of[T]
  val symbol = tpe.typeSymbol

  if !symbol.flags.is(Flags.Case) then
    report.errorAndAbort(s"CaseClass can be instantiated only for case classes, but got: ${summon[Type[T]]}")

  def name = symbol.name

  def fields: List[CaseClassField[Q, T]] =
    symbol.caseFields.zip(symbol.primaryConstructor.paramSymss.head).map { (caseField, constructorField) =>
      new CaseClassField[Q, T](using q, summon[Type[T]])(caseField, constructorField, tpe.memberType(caseField))
    }

  def instanceFromValues(values: Expr[Seq[Any]]): Expr[T] = '{
    val valuesVector = $values.toVector
    ${
      Apply(
        Select.unique(New(Inferred(tpe)), "<init>"),
        symbol.caseFields.zipWithIndex.map { (field: Symbol, i: Int) =>
          TypeApply(
            Select.unique('{ valuesVector.apply(${ Expr(i) }) }.asTerm, "asInstanceOf"),
            List(Inferred(tpe.memberType(field)))
          )
        }
      ).asExprOf[T]
    }
  }

  def instanceFromValues(values: Seq[Expr[Any]]): Expr[T] = {
    val valuesVector = values.toVector
    Apply(
      Select.unique(New(Inferred(tpe)), "<init>"),
      symbol.caseFields.zipWithIndex.map { (field: Symbol, i: Int) =>
        TypeApply(
          Select.unique(valuesVector(i).asTerm, "asInstanceOf"),
          List(Inferred(tpe.memberType(field)))
        )
      }
    ).asExprOf[T]
  }

  /** Extracts an optional argument from an annotation with a single string-valued argument with a default value. */
  def extractOptStringArgFromAnnotation(annSymbol: Symbol): Option[Option[String]] = {
    symbol.getAnnotation(annSymbol).map {
      case Apply(_, List(Select(_, "$lessinit$greater$default$1")))             => None
      case Apply(_, List(Literal(c: Constant))) if c.value.isInstanceOf[String] => Some(c.value.asInstanceOf[String])
      case _ => report.errorAndAbort(s"Cannot extract annotation: @${annSymbol.name}, from class: ${symbol.name}")
    }
  }
}

// The `symbol` is needed to get the field type and generate instance selects. The `constructorField` symbol is needed
// to read annotations.
private[tapir] class CaseClassField[Q <: Quotes, T](using val q: Q, t: Type[T])(
    val symbol: q.reflect.Symbol,
    constructorField: q.reflect.Symbol,
    val tpe: q.reflect.TypeRepr
) {
  import q.reflect.*

  def name = symbol.name

  /** Extracts an argument from an annotation with a single string-valued argument. */
  def extractStringArgFromAnnotation(annSymbol: Symbol): Option[String] = constructorField.getAnnotation(annSymbol).map {
    case Apply(_, List(Literal(c: Constant))) if c.value.isInstanceOf[String] => c.value.asInstanceOf[String]
    case _ => report.errorAndAbort(s"Cannot extract annotation: @${annSymbol.name}, from field: ${symbol.name}, of type: ${Type.show[T]}")
  }

  /** Extracts an optional argument from an annotation with a single string-valued argument with a default value. */
  def extractOptStringArgFromAnnotation(annSymbol: Symbol): Option[Option[String]] = {
    constructorField.getAnnotation(annSymbol).map {
      case Apply(_, List(Select(_, "$lessinit$greater$default$1")))             => None
      case Apply(_, List(Literal(c: Constant))) if c.value.isInstanceOf[String] => Some(c.value.asInstanceOf[String])
      case _ => report.errorAndAbort(s"Cannot extract annotation: @${annSymbol.name}, from field: ${symbol.name}, of type: ${Type.show[T]}")
    }
  }

  def extractTreeFromAnnotation(annSymbol: Symbol): Option[Tree] = constructorField.getAnnotation(annSymbol).map {
    case Apply(_, List(t)) => t
    case _ => report.errorAndAbort(s"Cannot extract annotation: @${annSymbol.name}, from field: ${symbol.name}, of type: ${Type.show[T]}")
  }

  def extractFirstTreeArgFromAnnotation(annSymbol: Symbol): Option[Tree] = constructorField.getAnnotation(annSymbol).map {
    case Apply(_, List(t, _*)) => t
    case _ => report.errorAndAbort(s"Cannot extract annotation: @${annSymbol.name}, from field: ${symbol.name}, of type: ${Type.show[T]}")
  }

  def annotated(annSymbol: Symbol): Boolean = annotation(annSymbol).isDefined
  def annotation(annSymbol: Symbol): Option[Term] = constructorField.getAnnotation(annSymbol)
}
