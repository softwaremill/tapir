package upickle.core

trait Types2 { types =>
  trait TestWrapper[T] extends Writer2[T] with Reader2[T] {
    def a: T
  }

  trait Reader2[T] {
    def b: T
  }

  trait Writer2[T] extends Visitor[Any, T] {
    def c: T
  }

}
