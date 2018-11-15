package sapi.internal

private[sapi] object SeqToParams {
  def apply[T](seq: Seq[T]): Any = {
    seq match {
      case Seq(v)                      => v
      case Seq(v1, v2)                 => (v1, v2)
      case Seq(v1, v2, v3)             => (v1, v2, v3)
      case Seq(v1, v2, v3, v4)         => (v1, v2, v3, v4)
      case Seq(v1, v2, v3, v4, v5)     => (v1, v2, v3, v4, v5)
      case Seq(v1, v2, v3, v4, v5, v6) => (v1, v2, v3, v4, v5, v6)
      case _                           => throw new IllegalArgumentException(s"Cannot convert $seq to a tuple!")
    }
  }
}
