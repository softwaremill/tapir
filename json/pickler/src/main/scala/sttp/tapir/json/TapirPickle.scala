package sttp.tapir.json

import _root_.upickle.AttributeTagged

trait TapirPickle[T] extends AttributeTagged with Readers with Writers:
  def reader: this.Reader[T]
  def writer: this.Writer[T]

  // This ensures that None is encoded as null instead of an empty array
  override given OptionWriter[T: Writer]: Writer[Option[T]] =
    summon[Writer[T]].comapNulls[Option[T]] {
      case None    => null.asInstanceOf[T]
      case Some(x) => x
    }

  // This ensures that null is read as None
  override given OptionReader[T: Reader]: Reader[Option[T]] =
    new Reader.Delegate[Any, Option[T]](summon[Reader[T]].map(Some(_))) {
      override def visitNull(index: Int) = None
    }
