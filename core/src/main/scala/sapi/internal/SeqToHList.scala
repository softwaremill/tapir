package sapi.internal

import shapeless.{HList, HNil}

private[sapi] object SeqToHList {
  def apply[T](seq: Seq[T]): HList = {
    var current: HList = HNil
    seq.reverse.foreach { x =>
      current = x :: current
    }
    current
  }
}
