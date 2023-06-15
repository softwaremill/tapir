package sttp.tapir.json.upickle.auto

import scala.quoted.*
import magnolia1.*

object Macro:

  inline def join2[T](inline ctx: CaseClass[TapirPickle.Writer, T]): TapirPickle.Writer[T] = ${ macroJoin[T]('ctx) }

  def macroJoin[T](ctx: Expr[CaseClass[TapirPickle.Writer, T]])(using Quotes): Expr[TapirPickle.Writer[T]] = {
    import quotes.reflect.*
    val caseClassType = TypeRepr.of[T].typeSymbol
    val classFields = caseClassType.primaryConstructor.paramSymss.flatten.filter(_.isValDef)
    val givenWritersExpr = classFields.map { field =>
      val fieldType = field.typeRef.typeSymbol
      tfplay
    }
  }
