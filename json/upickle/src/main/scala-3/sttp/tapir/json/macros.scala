package sttp.tapir.json.macros

import scala.quoted.*
import deriving.*, compiletime.*
import scala.reflect.ClassTag
import _root_.upickle.implicits.*
import _root_.upickle.implicits.{macros => uMacros}

type IsInt[A <: Int] = A

inline def writeSnippets[R, T](
    inline thisOuter: upickle.core.Types with upickle.implicits.MacrosCommon,
    inline self: upickle.implicits.CaseClassReadWriters#CaseClassWriter[T],
    inline v: T,
    inline ctx: _root_.upickle.core.ObjVisitor[_, R],
    childWriters: List[Any]
): Unit =
  ${ writeSnippetsImpl[R, T]('thisOuter, 'self, 'v, 'ctx, 'childWriters) }

def writeSnippetsImpl[R, T](
    thisOuter: Expr[upickle.core.Types with upickle.implicits.MacrosCommon],
    self: Expr[upickle.implicits.CaseClassReadWriters#CaseClassWriter[T]],
    v: Expr[T],
    ctx: Expr[_root_.upickle.core.ObjVisitor[_, R]],
    childWriters: Expr[List[?]]
)(using Quotes, Type[T], Type[R]): Expr[Unit] =

  import quotes.reflect.*

  Expr.block(
    for (((rawLabel, label), i) <- uMacros.fieldLabelsImpl0[T].zipWithIndex) yield {
      val tpe0 = TypeRepr.of[T].memberType(rawLabel).asType
      tpe0 match
        case '[tpe] =>
          val defaults = uMacros.getDefaultParamsImpl0[T]
          Literal(IntConstant(i)).tpe.asType match
            case '[IsInt[index]] =>
              val select = Select.unique(v.asTerm, rawLabel.name).asExprOf[Any]

              val snippet = '{
                ${ self }.writeSnippetMappedName[R, tpe](
                  ${ ctx },
                  ${ thisOuter }.objectAttributeKeyWriteMap(${ Expr(label) }),
                  ${ childWriters }(${ Expr(i) }),
                  ${ select }
                )
              }
              if (!defaults.contains(label)) snippet
              else '{ if (${ thisOuter }.serializeDefaults || ${ select } != ${ defaults(label) }) $snippet }

    },
    '{ () }
  )
