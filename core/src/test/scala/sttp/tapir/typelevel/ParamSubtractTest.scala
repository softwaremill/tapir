package sttp.tapir.typelevel

class ParamSubtractTest {
  implicitly[TupleHeadAndTail[(Int, String), Int, String]]
  implicitly[TupleHeadAndTail[(Int, String, Double), Int, (String, Double)]]
  implicitly[TupleHeadAndTail[((Int, Double), String), (Int, Double), String]]

  implicitly[ParamSubtract.Aux[Unit, Unit, Unit]]
  implicitly[ParamSubtract.Aux[String, Unit, String]]
  implicitly[ParamSubtract.Aux[(String, Int), Unit, (String, Int)]]
  implicitly[ParamSubtract.Aux[(String, Int, Double), Unit, (String, Int, Double)]]

  implicitly[ParamSubtract.Aux[String, String, Unit]]
  implicitly[ParamSubtract.Aux[(String, Int), (String, Int), Unit]]

  implicitly[ParamSubtract.Aux[(String, Int), String, Int]]
  implicitly[ParamSubtract.Aux[(String, Int, Double), String, (Int, Double)]]

  implicitly[ParamSubtract.Aux[(String, Int, Double), (String, Int), Double]]

  implicitly[ParamSubtract[String, Unit]]
  implicitly[ParamSubtract[String, String]]
  implicitly[ParamSubtract[(String, Int), Unit]]
  implicitly[ParamSubtract[(String, Int), String]]
  implicitly[ParamSubtract[(String, Int), (String, Int)]]
  implicitly[ParamSubtract[(String, Int, Double), String]]
  implicitly[ParamSubtract[(String, Int, Double), (String, Int)]]
  implicitly[ParamSubtract[(String, Int, Double), (String, Int, Double)]]
}
