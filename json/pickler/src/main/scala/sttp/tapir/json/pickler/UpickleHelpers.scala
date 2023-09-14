package sttp.tapir.json.pickler

private[pickler] trait UpickleHelpers {
  def scanChildren[T, V](xs: Seq[T])(f: T => V) = { // copied from uPickle
    var x: V = null.asInstanceOf[V]
    val i = xs.iterator
    while (x == null && i.hasNext) {
      val t = f(i.next())
      if (t != null) x = t
    }
    x
  }

}
