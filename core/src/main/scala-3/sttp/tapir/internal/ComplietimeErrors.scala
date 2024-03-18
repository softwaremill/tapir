package sttp.tapir.internal

import scala.quoted.*

private[tapir] object ComplietimeErrors {
  inline def reportIncorrectMapping[SOURCE, TARGET] = ${ reportIncorrectMappingImpl[SOURCE, TARGET] }

  private def reportIncorrectMappingImpl[SOURCE: Type, TARGET: Type](using Quotes): Expr[Unit] = {
    import quotes.reflect.*

    if TypeRepr.of[TARGET].typeSymbol.declaredFields.size > 22 then
      report.errorAndAbort(
        s"Cannot map to ${Type.show[TARGET]}: arities of up to 22 are supported. If you need more inputs/outputs, map them to classes with less fields, and then combine these classes."
      )
    else report.errorAndAbort(s"Failed to map ${Type.show[SOURCE]} into ${Type.show[TARGET]}")
  }

}
