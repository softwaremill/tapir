package sttp.tapir.json.pickler.next.internal.compiletime

import hearth.MacroCommonsScala3
import sttp.tapir.Schema
import sttp.tapir.internal.SNameMacros
import sttp.tapir.json.pickler.next.{Pickler, PicklerConfiguration}

import scala.quoted.*

/** Scala 3 macro bundle: the only file that knows about `Quotes`.
  *
  * `MacroCommonsScala3` supplies Hearth's cake (and already mixes in `StdExtensions`); all the actual derivation logic
  * comes from `PicklerMacrosImpl`. Adding Scala 2.13 support later means adding a sibling bundle extending
  * `MacroCommonsScala2` with the same `PicklerMacrosImpl` and its own [[PlatformSupport]] — nothing else changes.
  */
final private[next] class PicklerMacros(q: Quotes)
    extends MacroCommonsScala3(using q),
      LoadStandardExtensionsOnce,
      PicklerMacrosImpl,
      PlatformSupportScala3

private[next] object PicklerMacros {

  def derivePicklerImpl[A: Type](config: Expr[PicklerConfiguration])(using q: Quotes): Expr[Pickler[A]] =
    new PicklerMacros(q).derivePickler[A](config)

  def deriveSchemaOnlyImpl[A: Type](config: Expr[PicklerConfiguration])(using q: Quotes): Expr[Schema[A]] =
    new PicklerMacros(q).deriveSchemaOnly[A](config)
}

/** [[PlatformSupport]] for Scala 3.
  *
  * The two mapper shapes are pinned by jsoniter's `CompileTimeEval` (`NameMapper.scala` in `jsoniter-scala-macros`):
  *   - `evalApplyStringTerm` destructures the argument with the `Lambda(params, body)` extractor and evaluates the body
  *     as a `Match` with a `null` default, so a `Closure` over a `DefDef` whose body is a bare `Match` on `Literal`
  *     patterns is the shape to produce. This is exactly what the typer produces for `{ case "a" => "b" }` before
  *     `ExpandSAMs`, which is why hand-written jsoniter configurations work.
  *   - A `Map(...)` literal is matched by a quote pattern that does **not** see through the `Inlined` nodes splicing
  *     produces, so it is unusable from a macro (measured, D5).
  */
private[compiletime] trait PlatformSupportScala3 extends PlatformSupport { this: MacroCommonsScala3 =>
  import quotes.reflect.*

  private def stringMatch(pairs: List[(String, String)], scrutinee: Term, fallback: Option[Term]): Match =
    Match(
      scrutinee,
      pairs.map { case (k, v) => CaseDef(Literal(StringConstant(k)), None, Literal(StringConstant(v))) } ++
        fallback.map(f => CaseDef(Wildcard(), None, f)).toList
    )

  private val stringToString: MethodType =
    MethodType(List("x"))(_ => List(TypeRepr.of[String]), _ => TypeRepr.of[String])

  protected def stringPartialFunction(pairs: List[(String, String)]): Expr[PartialFunction[String, String]] = {
    val sym = Symbol.newMethod(Symbol.spliceOwner, "picklerFieldNameMapper", stringToString)
    val defDef = DefDef(
      sym,
      {
        case List(List(x: Term)) => Some(stringMatch(pairs, x, fallback = None))
        case other               => throw new IllegalStateException(s"unexpected lambda parameters: $other")
      }
    )
    Block(List(defDef), Closure(Ref(sym), Some(TypeRepr.of[PartialFunction[String, String]])))
      .asExprOf[PartialFunction[String, String]]
  }

  protected def stringFunction(pairs: List[(String, String)]): Expr[String => String] =
    Lambda(
      Symbol.spliceOwner,
      stringToString,
      {
        case (_, List(x: Term)) => stringMatch(pairs, x, fallback = Some(x))
        case (_, other)         => throw new IllegalStateException(s"unexpected lambda parameters: $other")
      }
    ).asExprOf[String => String]

  /** Mirrors `JsonCodecMakerInstance.discriminatorValue` in `jsoniter-scala-macros`: enum values are named by their
    * term symbol, everything else by its type symbol, and a module's trailing `$` is dropped.
    */
  protected def jsoniterLeafName[A: Type]: String = {
    val tpe = TypeRepr.of[A]
    val symbol = if (tpe.termSymbol.flags.is(Flags.Enum)) tpe.termSymbol else tpe.typeSymbol
    val name = symbol.fullName
    if (symbol.flags.is(Flags.Module)) name.substring(0, name.length - 1) else name
  }

  protected def tapirFullName[A: Type]: String = SNameMacros.typeFullNameFromTpe(TypeRepr.of[A])

  protected def implicitLazyVals[Out: Type](vals: List[(String, UntypedType, UntypedExpr)])(
      body: List[UntypedExpr] => Expr[Out]
  ): Expr[Out] = {
    val defs = vals.map { case (name, tpe, rhs) =>
      val sym = Symbol.newVal(Symbol.spliceOwner, name, tpe, Flags.Implicit | Flags.Lazy, Symbol.noSymbol)
      ValDef(sym, Some(rhs.changeOwner(sym)))
    }
    Block(defs, body(defs.map(d => Ref(d.symbol))).asTerm).asExprOf[Out]
  }

  protected def dereferenceStable[A: Type](expr: Expr[A]): Option[Expr[A]] = {
    def loop(term: Term): Option[Term] = term match {
      case Inlined(_, Nil, inner) => loop(inner)
      case Typed(inner, _)        => loop(inner)
      case ref: Ref if ref.symbol.isValDef =>
        ref.symbol.tree match {
          case ValDef(_, _, Some(rhs)) => Some(rhs)
          case _                       => None
        }
      case _ => None
    }
    loop(expr.asTerm).map(_.asExprOf[A])
  }
}
