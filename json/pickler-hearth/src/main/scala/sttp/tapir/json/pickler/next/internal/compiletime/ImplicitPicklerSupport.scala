package sttp.tapir.json.pickler.next.internal.compiletime

import hearth.MacroCommons
import hearth.fp.effect.*
import sttp.tapir.json.pickler.next.Pickler

import scala.collection.mutable

/** Looking up a user-supplied `Pickler[X]` for a type nested in the one being derived.
  *
  * ==Why this is not a plain `Expr.summonImplicit`==
  * With `generic.auto.*` in scope, `Pickler[X]` always has a candidate: `auto.picklerForType[X]`, which expands to `Pickler.derived[X]` —
  * our own macro. Summoning from inside a derivation would therefore start a *nested* derivation for every field type, recursing forever on
  * cyclic types and exponentially on deep ones (plan §6.4). `Implicits.searchIgnoring`, the direct fix, needs Scala 3.7.
  *
  * The guard is instead in `PicklerMacros.derivePicklerImpl`: it aborts immediately when invoked while another derivation is on the stack.
  * The compiler treats an aborted candidate as a failed one, so the search comes back empty and we derive structurally; a user's `given` is
  * an already-typed value, not a pending macro call, and is found normally.
  *
  * ==Exclusions==
  * The root type is never looked up (a `given p: Pickler[A] = Pickler.derived[A]` would otherwise find itself). `oneOfUsingField`
  * additionally excludes the leaves it maps, whose codecs it must derive with overridden discriminator values rather than take from the
  * user's child picklers.
  */
trait ImplicitPicklerSupport { this: MacroCommons =>

  private val memo = mutable.Map.empty[String, Option[Expr_??]]

  /** Types for which no user pickler is looked up (by `plainPrint`). Mutable per-expansion state, set by the entry points before derivation
    * starts.
    */
  protected var implicitLookupExclusions: Set[String] = Set.empty

  private def PicklerOf[A: Type]: Type[Pickler[A]] = Type.of[Pickler[A]]

  protected def userPickler[A: Type]: MIO[Option[Expr[Pickler[A]]]] = {
    val key = Type[A].plainPrint
    if (implicitLookupExclusions.contains(key)) MIO.pure(None)
    else
      memo.get(key) match {
        case Some(cached) => MIO.pure(cached.map(_.value.asInstanceOf[Expr[Pickler[A]]]))
        case None         =>
          implicit val PicklerA: Type[Pickler[A]] = PicklerOf[A]
          val found = Expr.summonImplicit[Pickler[A]].toOption
          memo.update(key, found.map(_.as_??))
          found match {
            case Some(expr) => Log.info(s"Using user-supplied Pickler for ${Type[A].prettyPrint}: ${expr.prettyPrint}").as(found)
            case None       => MIO.pure(None)
          }
      }
  }
}
