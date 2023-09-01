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
import _root_.upickle.implicits.{macros => upickleMacros}
import sttp.tapir.SchemaType.SProduct
import _root_.upickle.core.Annotator.Checker
import scala.quoted.*

trait Writers extends AttributeTagged with UpickleHelpers {

  inline def macroProductW[T: ClassTag](schema: Schema[T], childWriters: => List[Any], subtypeDiscriminator: SubtypeDiscriminator[T])(using
      Configuration
  ) =
    lazy val writer = new CaseClassWriter[T] {
      def length(v: T) = upickleMacros.writeLength[T](outerThis, v)

      val sProduct = schema.schemaType.asInstanceOf[SProduct[T]]

      override def write0[R](out: Visitor[_, R], v: T): R = {
        if (v == null) out.visitNull(-1)
        else {
          val ctx = out.visitObject(length(v), true, -1)
          macros.writeSnippets[R, T](
            sProduct,
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
          sProduct,
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
      annotate[T](
        writer,
        upickleMacros.tagName[T],
        Annotator.Checker.Cls(implicitly[ClassTag[T]].runtimeClass)
      ) // tagName is responsible for extracting the @tag annotation meaning the discriminator value
    else writer

  inline def macroSumW[T: ClassTag](inline schema: Schema[T], childWriters: => List[Any], subtypeDiscriminator: SubtypeDiscriminator[T])(
      using Configuration
  ) =
    implicit val currentlyDeriving: _root_.upickle.core.CurrentlyDeriving[T] = new _root_.upickle.core.CurrentlyDeriving()
    val writers: List[TaggedWriter[_ <: T]] = childWriters
      .asInstanceOf[List[TaggedWriter[_ <: T]]]

    new TaggedWriter.Node[T](writers: _*) {
      override def findWriter(v: Any): (String, ObjectWriter[T]) = {
        subtypeDiscriminator match {
          case discriminator: CustomSubtypeDiscriminator[T] =>
            val (tag, w) = super.findWriter(v)
            val overriddenTag = discriminator.writeUnsafe(v) // here we use our discirminator instead of uPickle's
            (overriddenTag, w)
          case _: DefaultSubtypeDiscriminator[T] =>
            super.findWriter(v)
        }
      }
    }
}
