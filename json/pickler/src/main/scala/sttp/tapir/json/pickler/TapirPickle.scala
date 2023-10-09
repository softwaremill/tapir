package sttp.tapir.json.pickler

import _root_.upickle.AttributeTagged
import upickle.Api

/** Our custom modification of uPickle encoding/decoding logic. A standard way to use uPickle is to import `upickle.default` object which
  * allows generating Reader[T]/Writer[T]. We create our own object with same API as `upickle.default`, but modified logic, which can be
  * found in Readers and Writers traits.
  */
private object TapirPickle extends AttributeTagged with Readers with Writers:
  type UWriter[T] = Writer[T]
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
