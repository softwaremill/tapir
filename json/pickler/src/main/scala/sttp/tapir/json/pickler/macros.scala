package sttp.tapir.json.pickler

import _root_.upickle.implicits.*
import _root_.upickle.implicits.{macros => uMacros}
import sttp.tapir.SchemaType
import sttp.tapir.SchemaType.SProduct

import scala.quoted.*

import compiletime.*

/** Macros, mostly copied from uPickle, and modified to allow our customizations like passing writers/readers as parameters, adjusting
  * encoding/decoding logic to make it coherent with the schema.
  */
private[pickler] object macros:
  type IsInt[A <: Int] = A

  private[pickler] inline def writeSnippets[R, T](
      inline sProduct: SProduct[T],
      inline thisOuter: upickle.core.Types with upickle.implicits.MacrosCommon,
      inline self: upickle.implicits.CaseClassReadWriters#CaseClassWriter[T],
      inline v: T,
      inline ctx: _root_.upickle.core.ObjVisitor[_, R],
      childWriters: List[Any],
      childDefaults: List[Option[Any]]
  ): Unit =
    ${ writeSnippetsImpl[R, T]('sProduct, 'thisOuter, 'self, 'v, 'ctx, 'childWriters, 'childDefaults) }

  private[pickler] def writeSnippetsImpl[R, T](
      sProduct: Expr[SProduct[T]],
      thisOuter: Expr[upickle.core.Types with upickle.implicits.MacrosCommon],
      self: Expr[upickle.implicits.CaseClassReadWriters#CaseClassWriter[T]],
      v: Expr[T],
      ctx: Expr[_root_.upickle.core.ObjVisitor[_, R]],
      childWriters: Expr[List[?]],
      childDefaults: Expr[List[Option[?]]]
  )(using Quotes, Type[T], Type[R]): Expr[Unit] =

    import quotes.reflect.*
    Expr.block(
      for (((rawLabel, label), i) <- uMacros.fieldLabelsImpl0[T].zipWithIndex) yield {
        val tpe0 = TypeRepr.of[T].memberType(rawLabel).asType
        tpe0 match
          case '[tpe] =>
            Literal(IntConstant(i)).tpe.asType match
              case '[IsInt[index]] =>
                val encodedName = '{ ${ sProduct }.fields(${ Expr(i) }).name.encodedName }
                val select = Select.unique(v.asTerm, rawLabel.name).asExprOf[Any]
                '{
                  ${ self }.writeSnippetMappedName[R, tpe](
                    ${ ctx },
                    ${ encodedName },
                    ${ childWriters }(${ Expr(i) }),
                    ${ select }
                  )
                }
      },
      '{ () }
    )

  private[pickler] inline def storeDefaultsTapir[T](
      inline x: upickle.implicits.BaseCaseObjectContext,
      defaultsFromSchema: List[Option[Any]]
  ): Unit = ${
    storeDefaultsImpl[T]('x, 'defaultsFromSchema)
  }

  private[pickler] def storeDefaultsImpl[T](x: Expr[upickle.implicits.BaseCaseObjectContext], defaultsFromSchema: Expr[List[Option[Any]]])(
      using
      Quotes,
      Type[T]
  ) = {
    import quotes.reflect.*

    val defaults = uMacros.getDefaultParamsImpl0[T]
    val statements = uMacros
      .fieldLabelsImpl0[T]
      .zipWithIndex
      .map { case ((rawLabel, label), i) =>
        Expr.block(
          List('{
            // modified uPickle macro - this additional expression looks for defaults in the schema
            // and applies them to override defaults from the type definition
            ${ defaultsFromSchema }(${ Expr(i) }).foreach { schemaDefaultValue =>
              ${ x }.storeValueIfNotFound(${ Expr(i) }, schemaDefaultValue)
            }
          }),
          if (defaults.contains(label)) '{ ${ x }.storeValueIfNotFound(${ Expr(i) }, ${ defaults(label) }) }
          else '{}
        )
      }

    Expr.block(statements, '{})
  }

  transparent inline def isScalaEnum[X]: Boolean = inline compiletime.erasedValue[X] match
    case _: Null         => false
    case _: Nothing      => false
    case _: reflect.Enum => allChildrenObjectsOrEnumCases[X]
    case _               => false

  /** Checks whether all children of type T are objects or enum cases or sealed parents of such. Useful for determining whether an enum is
    * indeed an enum, or will be desugared to a sealed hierarchy, in which case it's not really an enumeration in context of schemas and
    * JSON codecs.
    */
  inline def allChildrenObjectsOrEnumCases[T]: Boolean = ${ allChildrenObjectsOrEnumCasesImpl[T] }

  import scala.quoted._

  def allChildrenObjectsOrEnumCasesImpl[T: Type](using q: Quotes): Expr[Boolean] =
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]
    val symbol = tpe.typeSymbol
    Expr(symbol.children.nonEmpty && !symbol.children.exists(c => c.isClassDef && !(c.flags is Flags.Sealed)))
