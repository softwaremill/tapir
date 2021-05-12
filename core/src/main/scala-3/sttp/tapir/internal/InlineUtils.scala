package sttp.tapir.internal

import scala.quoted.*

import scala.deriving.Mirror

object InlineUtils {
    inline def reportIncorrectMapping[SOURCE, TARGET] = ${ reportIncorrectMappingImple[SOURCE, TARGET] }
    
    private def reportIncorrectMappingImple[SOURCE: Type, TARGET: Type](using Quotes): Expr[Unit] = {
        quotes.reflect.report.error(s"Failed to map ${Type.show[SOURCE]} into ${Type.show[TARGET]}")
        
        '{}
    }

}
