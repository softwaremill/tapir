package sttp.tapir.json.upickle.auto

import scala.quoted.*
import magnolia1.*
import sttp.tapir.json.upickle.auto.TapirPickle
import scala.deriving.Mirror
import scala.reflect.ClassTag

object TapirPickleMacro:

  inline def join2[T](inline ctx: CaseClass[TapirPickle#Writer, T], inline m: Mirror.Of[T], ct: ClassTag[T]): TapirPickle#Writer[T] = ${ 
    macroJoin[T]('ctx, 'm, 'ct) 
  }

  def macroJoin[T: Type](ctx: Expr[CaseClass[TapirPickle#Writer, T]], m: Expr[Mirror.Of[T]], ct: Expr[ClassTag[T]])(using q: Quotes): Expr[TapirPickle#Writer[T]] = {
    import quotes.reflect.*
    val caseClassType = TypeRepr.of[T].typeSymbol
    val classFields = caseClassType.primaryConstructor.paramSymss.flatten
    val givenWritersExpr = classFields
      .map { fieldSymbol =>
        report.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> 1")
        val fieldRepr = TypeRepr.of[T].memberType(fieldSymbol)
        report.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
        val i = 1
        val expr: Expr[Unit] = fieldRepr.asType match {
          case '[fieldType] =>
            // TODO hardcoded 0, just to test the idea. Ideally we'd need to match ctx.parameters with classFields
            '{ given TapirPickle#Writer[fieldType] = $ctx.parameters(0).typeclass.asInstanceOf[TapirPickle#Writer[fieldType]] }
        }

        val exprTerm = expr.asTerm
        println(exprTerm.show(using Printer.TreeShortCode))
        expr
      }
    val allExpr = givenWritersExpr ++ List(
          '{given classTagT: ClassTag[T] = $ct}
      )
    val summoned = '{ given mirrorT2: Mirror.Of[T] = $m; null } // TODO can't do TapirPickle#derived
    report.info(">>>>>>>>>>>>>>>>>>>>>. 5")
    Expr.block(allExpr, summoned)
  }
