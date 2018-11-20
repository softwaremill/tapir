package sapi.typelevel

import sapi.Void

class ParamConcatTest {
  // should compile

  // tuple 1
  implicitly[ParamConcat.Aux[Tuple1[String], (Long, Double), (String, Long, Double)]]
  implicitly[ParamConcat.Aux[(Long, Double), Tuple1[String], (Long, Double, String)]]
  implicitly[ParamConcat.Aux[Tuple1[String], Tuple1[Int], (String, Int)]]

  // tuple > 1
  implicitly[ParamConcat.Aux[(String, Int), (Long, Double), (String, Int, Long, Double)]]

  // single & tuple
  implicitly[ParamConcat.Aux[String, (Long, Double), (String, Long, Double)]]
  implicitly[ParamConcat.Aux[(Long, Double), String, (Long, Double, String)]]

  // single & single
  implicitly[ParamConcat.Aux[String, Long, (String, Long)]]

  // nothing & tuple
  implicitly[ParamConcat.Aux[Void, (Long, Double), (Long, Double)]]
  implicitly[ParamConcat.Aux[(Long, Double), Void, (Long, Double)]]

  // nothing & single
  implicitly[ParamConcat.Aux[Void, Int, Int]]
  implicitly[ParamConcat.Aux[Int, Void, Int]]

  // without aux
  implicitly[ParamConcat[Tuple1[String], (Long, Double)]]
  implicitly[ParamConcat[(String, Int), (Long, Double)]]
  implicitly[ParamConcat[String, (Long, Double)]]
  implicitly[ParamConcat[Void, (Long, Double)]]
  implicitly[ParamConcat[Void, Int]]
}
