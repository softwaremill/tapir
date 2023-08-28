package sttp.tapir.json

import _root_.upickle.AttributeTagged
import sttp.tapir.Codec.JsonCodec
import _root_.upickle.AttributeTagged
import sttp.tapir.Schema
import sttp.tapir.Codec
import scala.util.Try
import scala.util.Success
import sttp.tapir.DecodeResult.Error
import sttp.tapir.DecodeResult.Value
import scala.util.Failure
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import _root_.upickle.core.Visitor
import _root_.upickle.core.ObjVisitor
import _root_.upickle.core.ArrVisitor
import scala.compiletime.*
import scala.deriving.Mirror
import scala.util.NotGiven
import scala.reflect.ClassTag
import sttp.tapir.generic.Configuration
import _root_.upickle.core.*
import _root_.upickle.implicits.{macros => upickleMacros}

trait Readers extends AttributeTagged {
  inline def macroProductR[T](childReaders: Tuple)(using m: Mirror.ProductOf[T]): Reader[T] =
    val reader = new CaseClassReadereader[T](upickleMacros.paramsCount[T], upickleMacros.checkErrorMissingKeysCount[T]()) {
      override def visitors0 = childReaders
      override def fromProduct(p: Product): T = m.fromProduct(p)
      override def keyToIndex(x: String): Int = upickleMacros.keyToIndex[T](x)
      override def allKeysArray = upickleMacros.fieldLabels[T].map(_._2).toArray
      override def storeDefaults(x: _root_.upickle.implicits.BaseCaseObjectContext): Unit = upickleMacros.storeDefaults[T](x)
    }

    inline if upickleMacros.isSingleton[T] then annotate[T](SingletonReader[T](upickleMacros.getSingleton[T]), upickleMacros.tagName[T])
    else if upickleMacros.isMemberOfSealedHierarchy[T] then annotate[T](reader, upickleMacros.tagName[T])
    else reader

  inline def macroSumR[T](childReaders: Tuple): Reader[T] =
    implicit val currentlyDeriving: _root_.upickle.core.CurrentlyDeriving[T] = new _root_.upickle.core.CurrentlyDeriving()
    val readers: List[Reader[_ <: T]] = childReaders
      .toList
      .asInstanceOf[List[Reader[_ <: T]]]

    Reader.merge[T](readers: _*)
}
