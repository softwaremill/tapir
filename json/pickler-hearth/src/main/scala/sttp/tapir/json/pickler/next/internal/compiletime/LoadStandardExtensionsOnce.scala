package sttp.tapir.json.pickler.next.internal.compiletime

import hearth.MacroCommons
import hearth.fp.effect.MIO
import hearth.std.StdExtensions

/** Guarantees that Hearth's standard extensions are loaded exactly once per macro expansion.
  *
  * The standard extensions register the providers behind the `IsCollection`, `IsMap`, `IsValueType` and `IsOption` extractors. Without them
  * those extractor patterns silently never match, and collection/option fields fall through to the case-class rule or fail outright.
  *
  * Because a single `Pickler` derivation runs three sub-derivations (schema, encoder, decoder), each of which would naturally want to load
  * them, the `var` below is what keeps the ServiceLoader scan from running three times. It lives on a trait that is mixed into the bundle
  * class exactly once, which is what makes it per-expansion state.
  *
  * Rules: never call `Environment.loadStandardExtensions()` directly, and never call this from inside an `Expr.quote` or a builder callback
  * — load once, before any quotes are constructed.
  */
trait LoadStandardExtensionsOnce { this: MacroCommons & StdExtensions =>

  private var standardExtensionsLoaded: Boolean = false

  protected def ensureStandardExtensionsLoaded(): MIO[Unit] =
    if (standardExtensionsLoaded) MIO.pure(())
    else
      Environment.loadStandardExtensions().toMIO(allowFailures = false).map { _ =>
        standardExtensionsLoaded = true
        ()
      }
}
