package sttp.tapir.codec.zio.prelude.newtype

import zio.prelude.{Validator, AssertionError}

object PalindromeValidator
    extends Validator[String](str => if (str.reverse == str) Right(()) else Left(AssertionError.failure("isPalindrome")))
