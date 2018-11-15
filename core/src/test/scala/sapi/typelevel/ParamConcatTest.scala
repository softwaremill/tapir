package sapi.typelevel

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

  // nothing & tuple
  implicitly[ParamConcat.Aux[Nothing, (Long, Double), (Long, Double)]]
  implicitly[ParamConcat.Aux[(Long, Double), Nothing, (Long, Double)]]

  // nothing & single
  implicitly[ParamConcat.Aux[Nothing, Int, Int]]
  implicitly[ParamConcat.Aux[Int, Nothing, Int]]

  // without aux
  implicitly[ParamConcat[Tuple1[String], (Long, Double)]]
  implicitly[ParamConcat[(String, Int), (Long, Double)]]
  implicitly[ParamConcat[String, (Long, Double)]]
  implicitly[ParamConcat[Nothing, (Long, Double)]]
  implicitly[ParamConcat[Nothing, Int]]
}
