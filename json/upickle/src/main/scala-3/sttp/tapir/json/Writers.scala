package sttp.tapir.json

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
import _root_.upickle.implicits. { macros => upickleMacros }

trait Writers extends AttributeTagged {

  inline def macroProductW[T: ClassTag](schema: Schema[T], childWriters: => List[Any])(using Configuration) =
    lazy val writer = new CaseClassWriter[T] {
        def length(v: T) = upickleMacros.writeLength[T](outerThis, v)

        override def write0[R](out: Visitor[_, R], v: T): R = {
          if (v == null) out.visitNull(-1)
          else {
            val ctx = out.visitObject(length(v), true, -1)
            macros.writeSnippets[R, T](
              outerThis,
              this,
              v,
              ctx,
              childWriters
            )
            ctx.visitEnd(-1)
          }
        }

        def writeToObject[R](ctx: _root_.upickle.core.ObjVisitor[_, R], v: T): Unit =
          macros.writeSnippets[R, T](
            outerThis,
            this,
            v,
            ctx,
            childWriters
          )
      }

      inline if upickleMacros.isSingleton[T] then
        annotate[T](SingletonWriter[T](null.asInstanceOf[T]), upickleMacros.tagName[T], Annotator.Checker.Val(upickleMacros.getSingleton[T]))
      else if upickleMacros.isMemberOfSealedHierarchy[T] then
        annotate[T](writer, upickleMacros.tagName[T], Annotator.Checker.Cls(implicitly[ClassTag[T]].runtimeClass))
      else
        writer
}
