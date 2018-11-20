package sapi.typelevel

class ParamsAsArgsTest {
  // without aux
  implicitly[ParamsAsArgs[String]]
  implicitly[ParamsAsArgs[(Int, String)]]
}
