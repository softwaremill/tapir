package tapir

sealed trait Constraint[T] {
  def check(t: T): Boolean
}

object Constraint {
  case class Minimum[T: Numeric](value: T) extends Constraint[T] {
    override def check(actual: T): Boolean = implicitly[Numeric[T]].gteq(actual, value)
  }

  case class Pattern[T <: String](value: String) extends Constraint[T] {
    override def check(t: T): Boolean = {
      t.matches(value)
    }
  }

  case class MinSize[T <: Iterable[_]](value: Int) extends Constraint[T] {
    override def check(t: T): Boolean = {
      t.size >= value
    }
  }
}
