package sttp.tapir.internal

import scala.quoted.*

import scala.deriving.Mirror

private[tapir] object ComplietimeErrors {
  inline def reportIncorrectMapping[SOURCE, TARGET] = ${ reportIncorrectMappingImpl[SOURCE, TARGET] }

  private def reportIncorrectMappingImpl[SOURCE: Type, TARGET: Type](using Quotes): Expr[Unit] = {
    import quotes.reflect.*

    report.throwError(s"Failed to map ${Type.show[SOURCE]} into ${Type.show[TARGET]}")
  }

}
