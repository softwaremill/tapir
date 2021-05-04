package sttp.tapir.internal

import scala.quoted.*

class CaseClass[Q <: Quotes, T: Type](using val q: Q) {
  import q.reflect.*

  val tpe = TypeRepr.of[T]
  val symbol = tpe.typeSymbol

  if !symbol.flags.is(Flags.Case) then
    report.throwError(s"Form codec can only be derived for case classes, but got: ${summon[Type[T]]}")

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
            Select.unique('{valuesVector.apply(${Expr(i)})}.asTerm, "asInstanceOf"),
            List(Inferred(tpe.memberType(field)))
          )
        }
      ).asExprOf[T]
    }
  }
}

// The `symbol` is needed to get the field type and generate instance selects. The `constructorField` symbol is needed
// to read annotations.
class CaseClassField[Q <: Quotes, T](using val q: Q, t: Type[T])(val symbol: q.reflect.Symbol, constructorField: q.reflect.Symbol, val tpe: q.reflect.TypeRepr) {
  import q.reflect.*

  def name = symbol.name

  def extractArgFromAnnotation(annSymbol: Symbol): Option[String] = constructorField.getAnnotation(annSymbol).map {
    case Apply(_, List(Literal(c: Constant))) if c.value.isInstanceOf[String] => c.value.asInstanceOf[String]
    case _ => report.throwError(s"Cannot extract annotation: $annSymbol, from field: $symbol, of type: ${summon[Type[T]]}")
  }
}
