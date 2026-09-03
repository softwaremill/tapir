package sttp.tapir.json.pickler.next

/** Import `sttp.tapir.json.pickler.next.debug.logDerivationForPickler` to make the derivation macro log every step of its
  * work as compiler `info` messages.
  *
  * The equivalent global switch is the scalac option `-Xmacro-settings:tapirPickler.logDerivation=true`.
  */
package object debug {
  implicit val logDerivationForPickler: Pickler.LogDerivation = Pickler.LogDerivation
}
