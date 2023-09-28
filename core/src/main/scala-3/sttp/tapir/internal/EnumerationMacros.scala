package sttp.tapir.internal

import scala.quoted.*

private[tapir] object EnumerationMacros:

  transparent inline def isEnumeration[T]: Boolean = inline compiletime.erasedValue[T] match
    case _: Null         => false
    case _: Nothing      => false
    case _: reflect.Enum => allChildrenObjectsOrEnumerationCases[T]
    case _               => false

  /** Checks whether type T has child types, and all children of type T are objects or enum cases or sealed parents of such. Useful for
    * determining whether an enum is indeed an enum, or will be desugared to a sealed hierarchy, in which case it's not really an
    * enumeration in context of schemas and JSON codecs.
    */
  inline def allChildrenObjectsOrEnumerationCases[T]: Boolean = ${ allChildrenObjectsOrEnumerationCasesImpl[T] }

  def allChildrenObjectsOrEnumerationCasesImpl[T: Type](using q: Quotes): Expr[Boolean] =
    Expr(enumerationTypeChildren[T](failOnError = false).nonEmpty)

  /** Recursively scans a symbol and builds a list of all children and their children, as long as all of them are objects or enum cases or
    * sealed parents of such. Useful for determining whether an enum is indeed an enum, or will be desugared to a sealed hierarchy, in which
    * case it's not really an enumeration (in context of schemas and JSON codecs).
    */
  def enumerationTypeChildren[T: Type](failOnError: Boolean)(using q: Quotes): List[q.reflect.Symbol] =
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    val symbol = tpe.typeSymbol

    def flatChildren(s: Symbol): List[Symbol] = s.children.toList.flatMap { c =>
      if (c.isClassDef) {
        if (!(c.flags is Flags.Sealed))
          if (failOnError)
            report.errorAndAbort("All children must be objects or enum cases, or sealed parent of such.")
          else
            Nil
        else
          flatChildren(c)
      } else List(c)
    }

    flatChildren(symbol)
