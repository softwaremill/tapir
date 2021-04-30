package sttp.tapir.macros

import sttp.tapir.Validator

trait ValidatorMacros {

  /** Creates an enum validator where all subtypes of the sealed hierarchy `T` are `object`s.
    * This enumeration will only be used for documentation, as a value outside of the allowed values will not be
    * decoded in the first place (the decoder has no other option than to fail).
    */
  def derivedEnumeration[T]: Validator.Enumeration[T] = ??? // TODO
}
