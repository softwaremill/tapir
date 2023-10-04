package sttp.tapir.internal

import scala.quoted.*

private[tapir] object EnumerationMacros:

  /** Checks whether type T is an "enumeration", which means either Scalas 3 enum, or a sealed hierarchy where all members are case objects
    * or enum cases. Useful for deriving schemas and JSON codecs.
    */
  inline def isEnumeration[T]: Boolean = ${ allChildrenObjectsOrEnumerationCasesImpl[T] }

  def allChildrenObjectsOrEnumerationCasesImpl[T: Type](using q: Quotes): Expr[Boolean] =
    val typeChildren = enumerationTypeChildren[T](failOnError = false)
    Expr(typeChildren.nonEmpty && !typeChildren.exists(_.isEmpty))

  /** Recursively scans a symbol and builds a list of all children and their children, as long as all of them are objects or enum cases or
    * sealed parents of such. Useful for determining whether an enum is indeed an enum, or will be desugared to a sealed hierarchy, in which
    * case it's not really an enumeration (in context of schemas and JSON codecs).
    */
  def enumerationTypeChildren[T: Type](failOnError: Boolean)(using q: Quotes): List[Option[q.reflect.Symbol]] =
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val symbol = tpe.typeSymbol

    def flatChildren(s: Symbol): List[Option[Symbol]] = s.children.toList.flatMap { c =>
      if (c.isClassDef) {
        if (!(c.flags is Flags.Sealed))
          if (failOnError)
            report.errorAndAbort("All children must be objects or enum cases, or sealed parent of such.")
          else
            List(None)
        else
          flatChildren(c)
      } else List(Some(c))
    }

    flatChildren(symbol)
