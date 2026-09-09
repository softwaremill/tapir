package sttp.tapir.json.pickler.next.internal.compiletime

import hearth.MacroCommons

/** The few operations the codec derivation needs that Hearth does not abstract over, implemented per platform.
  *
  * Everything here exists because the codec half delegates to `jsoniter-scala-macros`' `JsonCodecMaker.make`, whose
  * configuration is interpreted at *its* expansion time by walking the argument tree (`CompileTimeEval` in
  * `jsoniter-scala-macros`). That interpreter accepts a narrow set of tree shapes, so the trees have to be built by
  * hand rather than with `Expr.quote`. See `doc/dev/pickler-decisions.md` D5 for what was measured.
  *
  * Keeping these behind an interface is what leaves `CodecDerivation` platform-independent: a Scala 2 bundle would
  * implement the same six methods against `scala.reflect.macros` (where jsoniter evaluates its config with `c.eval`
  * and therefore accepts different shapes).
  */
trait PlatformSupport { this: MacroCommons =>

  /** `{ case "k1" => "v1"; case "k2" => "v2" }: PartialFunction[String, String]` — for `withFieldNameMapper`.
    *
    * The caller guarantees `pairs` is non-empty; an empty mapper should simply not be passed to jsoniter.
    */
  protected def stringPartialFunction(pairs: List[(String, String)]): Expr[PartialFunction[String, String]]

  /** `(x: String) => x match { case "k1" => "v1"; ...; case other => other }` — for `withAdtLeafClassNameMapper`. */
  protected def stringFunction(pairs: List[(String, String)]): Expr[String => String]

  /** The name jsoniter hands to `adtLeafClassNameMapper` for this leaf: `Symbol.fullName`, module `$` stripped. */
  protected def jsoniterLeafName[A: Type]: String

  /** The fully-qualified name tapir core's `SNameMacros.typeFullName[A]` produces — the base of the `SName` that
    * `SchemaDerivation.sNameExpr` builds, hence the input to `toDiscriminatorValue` on the schema side.
    */
  protected def tapirFullName[A: Type]: String

  /** `{ implicit lazy val n1: T1 = e1; ...; body(refs) }` where `refs` are references to the vals, in order.
    *
    * `implicit` because jsoniter finds codecs for nested types through `Implicits.search` and nothing else; `lazy`
    * so that mutually recursive codecs can refer to each other regardless of declaration order. Each right-hand side
    * is built from the same `refs`, so a hand-written combinator (`Either`) can name its sibling codecs directly
    * rather than through an implicit search that happens at *our* expansion time, when the vals do not exist yet.
    */
  protected def implicitLazyVals[Out: Type](vals: List[(String, UntypedType, List[UntypedExpr] => UntypedExpr)])(
      body: List[UntypedExpr] => Expr[Out]
  ): Expr[Out]

  /** `f(a)` with the lambda inlined, when `f` is a lambda literal: `((x: A) => body)(a)` becomes `body[x := a]`.
    *
    * Hearth's `semiEval` can evaluate a method-call tree but not apply a lambda it has evaluated (it materialises
    * lambdas as reflective proxies), so `oneOfUsingField` reduces the application first and evaluates the body.
    * Returns the plain application when `f` is not a literal lambda.
    */
  protected def betaReduce[A: Type, B: Type](f: Expr[A => B], a: Expr[A]): Expr[B]

  /** A string interpolation over constants, folded: `s"code-${200}"` gives `"code-200"`.
    *
    * Covers the one shape Hearth's `semiEval` does not (`StringContext.apply(parts*).s(args*)`, a varargs call on a
    * varargs-constructed receiver), which happens to be how `oneOfUsingField`'s `asString` is usually written.
    * `None` for anything else.
    */
  protected def constantInterpolation(expr: Expr[String]): Option[String]

  /** Follow a stable reference (`Ident`/`Select` of a `val`/`given`) to its right-hand side, when the definition's
    * tree is available in this compilation run. `None` when `expr` is not such a reference or the tree is not
    * retained (definitions from other compilation units need `-Yretain-trees`).
    */
  protected def dereferenceStable[A: Type](expr: Expr[A]): Option[Expr[A]]
}
