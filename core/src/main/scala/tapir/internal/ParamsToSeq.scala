package tapir.internal

private[tapir] object ParamsToSeq {
  def apply[T](a: Any): Seq[_] = {
    a match {
      case (v1, v2, v3, v4, v5, v6) => Seq(v1, v2, v3, v4, v5, v6)
      case (v1, v2, v3, v4, v5)     => Seq(v1, v2, v3, v4, v5)
      case (v1, v2, v3, v4)         => Seq(v1, v2, v3, v4)
      case (v1, v2, v3)             => Seq(v1, v2, v3)
      case (v1, v2)                 => Seq(v1, v2)
      case ()                       => Seq()
      case v1                       => Seq(v1) // single value is a catch-all so must be last
    }
  }
}
