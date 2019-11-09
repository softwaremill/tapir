package sttp.tapir.typelevel

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

  // unit & unit
  implicitly[ParamConcat.Aux[Unit, Unit, Unit]]

  // unit & tuple
  implicitly[ParamConcat.Aux[Unit, (Long, Double), (Long, Double)]]
  implicitly[ParamConcat.Aux[(Long, Double), Unit, (Long, Double)]]

  // unit & single
  implicitly[ParamConcat.Aux[Unit, Int, Int]]
  implicitly[ParamConcat.Aux[Int, Unit, Int]]

  // without aux
  implicitly[ParamConcat[Tuple1[String], (Long, Double)]]
  implicitly[ParamConcat[(String, Int), (Long, Double)]]
  implicitly[ParamConcat[String, (Long, Double)]]
  implicitly[ParamConcat[Unit, (Long, Double)]]
  implicitly[ParamConcat[Unit, Int]]
}
