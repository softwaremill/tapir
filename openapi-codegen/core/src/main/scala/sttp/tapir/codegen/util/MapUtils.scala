package sttp.tapir.codegen.util

object MapUtils {

  def merge[A, B](m1: Map[A, Seq[B]], m2: Map[A, Seq[B]]): Map[A, Seq[B]] =
    m2.iterator.foldLeft(m1) { case (m, (k, v)) =>
      m.get(k) match {
        case Some(value) => m + (k -> (value ++ v))
        case None => m + (k -> v)
      }
    }
}
