package sttp.tapir.json.pickler.next.internal.compiletime

import hearth.MacroCommonsScala3
import sttp.tapir.Schema
import sttp.tapir.json.pickler.next.{Pickler, PicklerConfiguration}

import scala.quoted.*

/** Scala 3 macro bundle: the only file that knows about `Quotes`.
  *
  * `MacroCommonsScala3` supplies Hearth's cake (and already mixes in `StdExtensions`); all the actual derivation logic
  * comes from `PicklerMacrosImpl`. Adding Scala 2.13 support later means adding a sibling bundle extending
  * `MacroCommonsScala2` with the same `PicklerMacrosImpl` — nothing else changes.
  */
final private[next] class PicklerMacros(q: Quotes)
    extends MacroCommonsScala3(using q),
      LoadStandardExtensionsOnce,
      PicklerMacrosImpl

private[next] object PicklerMacros {

  def derivePicklerImpl[A: Type](config: Expr[PicklerConfiguration])(using q: Quotes): Expr[Pickler[A]] =
    new PicklerMacros(q).derivePickler[A](config)

  def deriveSchemaOnlyImpl[A: Type](config: Expr[PicklerConfiguration])(using q: Quotes): Expr[Schema[A]] =
    new PicklerMacros(q).deriveSchemaOnly[A](config)
}
