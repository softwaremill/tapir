package tapir.internal

object ProductToParams {
  def apply(p: Product, arity: Int): Any = {
    arity match {
      case 0 => ()
      case 1 => p.productElement(0)
      case 2 => (p.productElement(0), p.productElement(1))
      case 3 => (p.productElement(0), p.productElement(1), p.productElement(2))
      case 4 => (p.productElement(0), p.productElement(1), p.productElement(2), p.productElement(3))
      case 5 => (p.productElement(0), p.productElement(1), p.productElement(2), p.productElement(3), p.productElement(4))
      case 6 =>
        (p.productElement(0), p.productElement(1), p.productElement(2), p.productElement(3), p.productElement(4), p.productElement(5))
    }
  }
}
