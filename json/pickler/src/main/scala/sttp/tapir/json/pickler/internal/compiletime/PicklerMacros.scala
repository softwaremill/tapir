package sttp.tapir.json.pickler.internal.compiletime

import hearth.MacroCommonsScala3
import sttp.tapir.Schema
import sttp.tapir.internal.SNameMacros
import sttp.tapir.json.pickler.{CreateDerivedEnumerationPickler, Pickler, PicklerConfiguration}

import scala.quoted.*

/** Scala 3 macro bundle: the only file that knows about `Quotes`.
  *
  * `MacroCommonsScala3` supplies Hearth's cake (and already mixes in `StdExtensions`); all the actual derivation logic comes from
  * `PicklerMacrosImpl`. Adding Scala 2.13 support later means adding a sibling bundle extending `MacroCommonsScala2` with the same
  * `PicklerMacrosImpl` and its own [[PlatformSupport]] — nothing else changes.
  */
final private[pickler] class PicklerMacros(q: Quotes)
    extends MacroCommonsScala3(using q),
      LoadStandardExtensionsOnce,
      PicklerMacrosImpl,
      PlatformSupportScala3

private[pickler] object PicklerMacros {

  /** Number of pickler derivations currently on the stack of this compiler thread.
    *
    * A derivation summons `Pickler[X]` for every nested `X` (see [[ImplicitPicklerSupport]]). If `generic.auto` is in scope, one candidate
    * is `Pickler.derived[X]` itself, i.e. this macro, expanded *while* the outer one is running. `derivePicklerImpl` refuses to run in that
    * situation, which turns the candidate into a failed one and lets the search fall through to "no user-supplied pickler". A `ThreadLocal`
    * rather than a plain `var` only because nothing guarantees the compiler will never expand macros on several threads.
    */
  private val depth: ThreadLocal[Int] = ThreadLocal.withInitial(() => 0)

  private def nested[R](body: => R): R = {
    depth.set(depth.get + 1)
    try body
    finally depth.set(depth.get - 1)
  }

  def derivePicklerImpl[A: Type](config: Expr[PicklerConfiguration])(using q: Quotes): Expr[Pickler[A]] =
    if (depth.get > 0)
      // Reached only as an implicit candidate during another derivation's `summon[Pickler[X]]`. Aborting here is
      // what makes that summon fail cleanly instead of deriving `X` a second time (or forever, for cyclic types).
      q.reflect.report.errorAndAbort(
        s"Pickler.derived[${q.reflect.TypeRepr.of[A].show}] invoked from within another Pickler derivation; " +
          "nested types are derived structurally unless a user-defined Pickler is in scope."
      )
    else nested(new PicklerMacros(q).derivePickler[A](config))

  def deriveSchemaOnlyImpl[A: Type](config: Expr[PicklerConfiguration])(using q: Quotes): Expr[Schema[A]] =
    nested(new PicklerMacros(q).deriveSchemaOnly[A](config))

  def derivedEnumerationImpl[A: Type](config: Expr[PicklerConfiguration])(using q: Quotes): Expr[CreateDerivedEnumerationPickler[A]] =
    nested(new PicklerMacros(q).deriveEnumerationBuilder[A](config))

  def oneOfUsingFieldImpl[A: Type, V: Type](
      extractor: Expr[A => V],
      asString: Expr[V => String],
      mapping: Expr[Seq[(V, Pickler[? <: A])]],
      config: Expr[PicklerConfiguration]
  )(using q: Quotes): Expr[Pickler[A]] =
    nested(new PicklerMacros(q).deriveOneOfUsingField[A, V](extractor, asString, mapping, config))
}

/** [[PlatformSupport]] for Scala 3.
  *
  * The two mapper shapes are pinned by jsoniter's `CompileTimeEval` (`NameMapper.scala` in `jsoniter-scala-macros`):
  *   - `evalApplyStringTerm` destructures the argument with the `Lambda(params, body)` extractor and evaluates the body as a `Match` with a
  *     `null` default, so a `Closure` over a `DefDef` whose body is a bare `Match` on `Literal` patterns is the shape to produce. This is
  *     exactly what the typer produces for `{ case "a" => "b" }` before `ExpandSAMs`, which is why hand-written jsoniter configurations
  *     work.
  *   - A `Map(...)` literal is matched by a quote pattern that does **not** see through the `Inlined` nodes splicing produces, so it is
  *     unusable from a macro (measured, D5).
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

  /** Mirrors `JsonCodecMakerInstance.discriminatorValue` in `jsoniter-scala-macros`: enum values are named by their term symbol, everything
    * else by its type symbol, and a module's trailing `$` is dropped.
    */
  protected def jsoniterLeafName[A: Type]: String = {
    val tpe = TypeRepr.of[A]
    val symbol = if (tpe.termSymbol.flags.is(Flags.Enum)) tpe.termSymbol else tpe.typeSymbol
    val name = symbol.fullName
    if (symbol.flags.is(Flags.Module)) name.substring(0, name.length - 1) else name
  }

  protected def tapirFullName[A: Type]: String = SNameMacros.typeFullNameFromTpe(TypeRepr.of[A])

  protected def implicitLazyVals[Out: Type](vals: List[(String, UntypedType, List[UntypedExpr] => UntypedExpr)])(
      body: List[UntypedExpr] => Expr[Out]
  ): Expr[Out] = {
    val syms = vals.map { case (name, tpe, _) =>
      Symbol.newVal(Symbol.spliceOwner, name, tpe, Flags.Implicit | Flags.Lazy, Symbol.noSymbol)
    }
    val refs: List[UntypedExpr] = syms.map(Ref(_))
    val defs = vals.zip(syms).map { case ((_, _, rhs), sym) => ValDef(sym, Some(rhs(refs).changeOwner(sym))) }
    Block(defs, body(refs).asTerm).asExprOf[Out]
  }

  protected def betaReduce[A: Type, B: Type](f: Expr[A => B], a: Expr[A]): Expr[B] = {
    val application = '{ $f($a) }
    Term.betaReduce(application.asTerm).fold(application)(_.asExprOf[B])
  }

  protected def constantInterpolation(expr: Expr[String]): Option[String] = {
    def constant(term: Term): Option[Any] = term match {
      case Inlined(_, Nil, inner) => constant(inner)
      case Typed(inner, _)        => constant(inner)
      case Literal(c)             => Some(c.value)
      case _                      => None
    }
    expr match {
      case '{ StringContext(${ Varargs(parts) }*).s(${ Varargs(args) }*) } =>
        for {
          ps <- parts.foldRight(Option(List.empty[String]))((p, acc) => acc.flatMap(tail => p.value.map(_ :: tail)))
          as <- args.foldRight(Option(List.empty[Any]))((a, acc) => acc.flatMap(tail => constant(a.asTerm).map(_ :: tail)))
        } yield StringContext(ps*).s(as*)
      case _ => None
    }
  }

  protected def dropNamedArgs[A: Type](expr: Expr[A]): Expr[A] = {
    val transform = new TreeMap {
      override def transformTerm(tree: Term)(owner: Symbol): Term = tree match {
        case NamedArg(_, arg) => transformTerm(arg)(owner)
        case other            => super.transformTerm(other)(owner)
      }
    }
    transform.transformTerm(expr.asTerm)(Symbol.spliceOwner).asExprOf[A]
  }

  protected def dereferenceStable[A: Type](expr: Expr[A]): Option[Expr[A]] = {
    def loop(term: Term): Option[Term] = term match {
      case Inlined(_, Nil, inner)          => loop(inner)
      case Typed(inner, _)                 => loop(inner)
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
