package sttp.tapir

import java.nio.charset.Charset

import sttp.model.{Method, MultiQueryParams}
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.EndpointIO.Info
import sttp.tapir.internal._
import sttp.tapir.model.ServerRequest
import sttp.tapir.typelevel.{FnComponents, ParamConcat}

import scala.collection.immutable.ListMap

sealed trait EndpointInput[T] extends EndpointIO.Mappable[T] {
  private[tapir] type ThisType[X] <: EndpointInput[X]

  def and[U, TU](other: EndpointInput[U])(implicit ts: ParamConcat.Aux[T, U, TU]): EndpointInput[TU]
  def /[U, TU](other: EndpointInput[U])(implicit ts: ParamConcat.Aux[T, U, TU]): EndpointInput[TU] = and(other)

  def show: String
}

object EndpointInput {
  sealed trait Single[T] extends EndpointInput[T] {
    private[tapir] type ThisType[X] <: EndpointInput.Single[X]

    def and[U, TU](other: EndpointInput[U])(implicit concat: ParamConcat.Aux[T, U, TU]): EndpointInput[TU] =
      other match {
        case Multiple(inputs, _, _) if concat.rightIsTuple         => Multiple.combine(this +: inputs, concat, this, other)
        case EndpointIO.Multiple(ios, _, _) if concat.rightIsTuple => Multiple.combine(this +: ios, concat, this, other)
        case i                                                     => Multiple.combine(Vector(this, i), concat, this, other)
      }
  }

  sealed trait Basic[T] extends Single[T] with EndpointIO.HasMetadata[T]

  case class FixedMethod[T](m: Method, codec: Codec[Unit, T, TextPlain], info: EndpointIO.Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = FixedMethod[X]
    override private[tapir] type L = Unit
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Unit, U, TextPlain], i: Info[U]): FixedMethod[U] = copy(codec = c, info = i)
    override def show: String = m.method
  }

  case class FixedPath[T](s: String, codec: Codec[Unit, T, TextPlain], info: EndpointIO.Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = FixedPath[X]
    override private[tapir] type L = Unit
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Unit, U, TextPlain], i: Info[U]): FixedPath[U] = copy(codec = c, info = i)
    override def show = s"/$s"
  }

  case class PathCapture[T](name: Option[String], codec: Codec[String, T, TextPlain], info: EndpointIO.Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = PathCapture[X]
    override private[tapir] type L = String
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[String, U, TextPlain], i: Info[U]): PathCapture[U] = copy(codec = c, info = i)
    override def show: String = addValidatorShow(s"/[${name.getOrElse("")}]", codec.validator)

    def name(n: String): PathCapture[T] = copy(name = Some(n))
  }

  case class PathsCapture[T](codec: Codec[List[String], T, TextPlain], info: EndpointIO.Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = PathsCapture[X]
    override private[tapir] type L = List[String]
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[List[String], U, TextPlain], i: Info[U]): PathsCapture[U] = copy(codec = c, info = i)
    override def show = s"/..."
  }

  case class Query[T](name: String, codec: Codec[List[String], T, TextPlain], info: EndpointIO.Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = Query[X]
    override private[tapir] type L = List[String]
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[List[String], U, TextPlain], i: Info[U]): Query[U] = copy(codec = c, info = i)
    override def show: String = addValidatorShow(s"?$name", codec.validator)
  }

  case class QueryParams[T](codec: Codec[MultiQueryParams, T, TextPlain], info: EndpointIO.Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = QueryParams[X]
    override private[tapir] type L = MultiQueryParams
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[MultiQueryParams, U, TextPlain], i: Info[U]): QueryParams[U] =
      copy(codec = c, info = i)
    override def show: String = s"?..."
  }

  case class Cookie[T](name: String, codec: Codec[Option[String], T, TextPlain], info: EndpointIO.Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = Cookie[X]
    override private[tapir] type L = Option[String]
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Option[String], U, TextPlain], i: Info[U]): Cookie[U] = copy(codec = c, info = i)
    override def show: String = addValidatorShow(s"{cookie $name}", codec.validator)
  }

  case class ExtractFromRequest[T](codec: Codec[ServerRequest, T, TextPlain], info: EndpointIO.Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = ExtractFromRequest[X]
    override private[tapir] type L = ServerRequest
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[ServerRequest, U, TextPlain], i: Info[U]): ExtractFromRequest[U] =
      copy(codec = c, info = i)
    override def show: String = s"{data from request}"
  }

  //

  trait Auth[T] extends EndpointInput.Single[T] {
    def input: EndpointInput.Single[T]
  }

  object Auth {
    case class ApiKey[T](input: EndpointInput.Single[T]) extends Auth[T] {
      override private[tapir] type ThisType[X] = ApiKey[X]
      override def show: String = s"auth(api key, via ${input.show})"
      override def map[U](mapping: Mapping[T, U]): ApiKey[U] = copy(input = input.map(mapping))
    }
    case class Http[T](scheme: String, input: EndpointInput.Single[T]) extends Auth[T] {
      override private[tapir] type ThisType[X] = Http[X]
      override def show: String = s"auth($scheme http, via ${input.show})"
      override def map[U](mapping: Mapping[T, U]): Http[U] = copy(input = input.map(mapping))
    }
    case class Oauth2[T](
        authorizationUrl: String,
        tokenUrl: String,
        scopes: ListMap[String, String],
        refreshUrl: Option[String] = None,
        input: EndpointInput.Single[T]
    ) extends Auth[T] {
      override private[tapir] type ThisType[X] = Oauth2[X]
      override def show: String = s"auth(oauth2, via ${input.show})"
      override def map[U](mapping: Mapping[T, U]): Oauth2[U] = copy(input = input.map(mapping))

      def requiredScopes(requiredScopes: Seq[String]): ScopedOauth2[T] = ScopedOauth2(this, requiredScopes)
    }

    case class ScopedOauth2[T](oauth2: Oauth2[T], requiredScopes: Seq[String]) extends Auth[T] {
      require(requiredScopes.forall(oauth2.scopes.keySet.contains), "all requiredScopes have to be defined on outer Oauth2#scopes")

      override private[tapir] type ThisType[X] = ScopedOauth2[X]
      override def show: String = s"scoped(${oauth2.show})"
      override def map[U](mapping: Mapping[T, U]): ScopedOauth2[U] = copy(oauth2 = oauth2.map(mapping))

      override def input: Single[T] = oauth2.input
    }
  }

  //

  case class MappedMultiple[PARAMS, T](input: Multiple[PARAMS], mapping: Mapping[PARAMS, T]) extends EndpointInput.Single[T] {
    override private[tapir] type ThisType[X] = MappedMultiple[PARAMS, X]
    override def show: String = input.show
    override def map[U](m: Mapping[T, U]): MappedMultiple[PARAMS, U] = copy[PARAMS, U](input, mapping.map(m))
  }

  case class Multiple[PARAMS](
      inputs: Vector[EndpointInput[_]],
      private[tapir] val mkParams: MkParams,
      private[tapir] val unParams: UnParams
  ) extends EndpointInput[PARAMS] {
    override private[tapir] type ThisType[X] = EndpointInput[X]

    override def show: String = if (inputs.isEmpty) "-" else inputs.map(_.show).mkString(" ")
    override def map[U](m: Mapping[PARAMS, U]): EndpointInput[U] =
      MappedMultiple[PARAMS, PARAMS](this, Mapping.id).map(m)

    override def and[U, TU](other: EndpointInput[U])(implicit concat: ParamConcat.Aux[PARAMS, U, TU]): EndpointInput[TU] =
      if (inputs.isEmpty)
        other.asInstanceOf[EndpointInput[TU]] // the other multiple must correspond to Unit, which is a neutral element of concatenation
      else
        other match {
          case Multiple(m, _, _) if concat.bothTuples              => Multiple.combine(inputs ++ m, concat, this, other)
          case Multiple(m, _, _) if concat.rightIsTuple            => Multiple.combine(this +: m, concat, this, other)
          case EndpointIO.Multiple(m, _, _) if concat.bothTuples   => Multiple.combine(inputs ++ m, concat, this, other)
          case EndpointIO.Multiple(m, _, _) if concat.rightIsTuple => Multiple.combine(this +: m, concat, this, other)
          case i if concat.leftIsTuple                             => Multiple.combine(inputs :+ i, concat, this, other)
          case i                                                   => Multiple.combine(Vector(this, i), concat, this, other)
        }
  }

  object Multiple {
    private[tapir] def combine[R, T, U](
        combinedInputs: Vector[EndpointInput[_]],
        concat: ParamConcat[T, U],
        left: EndpointInput[_],
        right: EndpointInput[_]
    ): Multiple[R] = {
      def fromInput(i: EndpointInput[_]): (MkParams, UnParams) = i match {
        case EndpointInput.Multiple(_, m, u) => (m, u)
        case EndpointIO.Multiple(_, m, u)    => (m, u)
        case _                               => (MkParams.Single, UnParams.Single)
      }
      val (mkParamsLeft, unParamsLeft) = fromInput(left)
      val (mkParamsRight, unParamsRight) = fromInput(right)
      Multiple(combinedInputs, combineMkParams(mkParamsLeft, mkParamsRight, concat), combineUnParams(unParamsLeft, unParamsRight, concat))
    }
  }
}

sealed trait EndpointOutput[T] extends EndpointIO.Mappable[T] {
  private[tapir] type ThisType[X] <: EndpointOutput[X]

  def and[J, IJ](other: EndpointOutput[J])(implicit ts: ParamConcat.Aux[T, J, IJ]): EndpointOutput[IJ]

  def show: String
}

object EndpointOutput {
  sealed trait Single[T] extends EndpointOutput[T] {
    private[tapir] def _mapping: Mapping[_, T]

    def and[U, TU](other: EndpointOutput[U])(implicit concat: ParamConcat.Aux[T, U, TU]): EndpointOutput[TU] =
      other match {
        case Multiple(outputs, _, _) if concat.rightIsTuple        => Multiple.combine(this +: outputs, concat, this, other)
        case EndpointIO.Multiple(ios, _, _) if concat.rightIsTuple => Multiple.combine(this +: ios, concat, this, other)
        case Void()                                                => this.asInstanceOf[EndpointOutput[TU]]
        case o                                                     => Multiple.combine(Vector(this, o), concat, this, other)
      }
  }

  sealed trait Basic[T] extends Single[T] with EndpointIO.HasMetadata[T] {
    override private[tapir] def _mapping: Mapping[_, T] = codec
  }

  //

  case class StatusCode[T](
      documentedCodes: Map[sttp.model.StatusCode, Info[Unit]],
      codec: Codec[sttp.model.StatusCode, T, TextPlain],
      info: Info[T]
  ) extends Basic[T] {
    override private[tapir] type ThisType[X] = StatusCode[X]
    override private[tapir] type L = sttp.model.StatusCode
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[sttp.model.StatusCode, U, TextPlain], i: Info[U]): StatusCode[U] =
      copy(codec = c, info = i)
    override def show: String = s"status code - possible codes ($documentedCodes)"

    def description(code: sttp.model.StatusCode, d: String): StatusCode[T] = {
      val updatedCodes = documentedCodes + (code -> Info.empty[Unit].description(d))
      copy(documentedCodes = updatedCodes)
    }
  }

  //

  case class FixedStatusCode[T](statusCode: sttp.model.StatusCode, codec: Codec[Unit, T, TextPlain], info: Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = FixedStatusCode[X]
    override private[tapir] type L = Unit
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Unit, U, TextPlain], i: Info[U]): FixedStatusCode[U] = copy(codec = c, info = i)
    override def show: String = s"status code ($statusCode)"
  }

  /**
    * Specifies that for `statusCode`, the given `output` should be used.
    *
    * The `appliesTo` function should determine, whether a runtime value matches the type `O`.
    * This check cannot be in general done by checking the run-time class of the value, due to type erasure (if `O` has
    * type parameters).
    */
  case class StatusMapping[O] private[tapir] (
      statusCode: Option[sttp.model.StatusCode],
      output: EndpointOutput[O],
      appliesTo: Any => Boolean
  )

  case class OneOf[O, T](mappings: Seq[StatusMapping[_ <: O]], codec: Mapping[O, T]) extends Single[T] {
    override private[tapir] type ThisType[X] = OneOf[O, X]
    override private[tapir] def _mapping: Mapping[_, T] = codec
    override def map[U](mapping: Mapping[T, U]): OneOf[O, U] = copy[O, U](codec = codec.map(mapping))
    override def show: String = s"status one of(${mappings.map(_.output.show).mkString("|")})"
  }

  //

  case class Void[T]() extends EndpointOutput[T] {
    override private[tapir] type ThisType[X] = Void[X]
    override def show: String = "void"
    override def map[U](mapping: Mapping[T, U]): Void[U] = Void()

    override def and[U, TU](other: EndpointOutput[U])(implicit ts: ParamConcat.Aux[T, U, TU]): EndpointOutput[TU] =
      other.asInstanceOf[EndpointOutput[TU]]
  }

  //

  case class MappedMultiple[PARAMS, T](output: Multiple[PARAMS], mapping: Mapping[PARAMS, T]) extends EndpointOutput.Single[T] {
    override private[tapir] type ThisType[X] = MappedMultiple[PARAMS, X]
    override private[tapir] def _mapping: Mapping[_, T] = mapping
    override def show: String = output.show
    override def map[U](m: Mapping[T, U]): MappedMultiple[PARAMS, U] = copy[PARAMS, U](output, mapping.map(m))
  }

  case class Multiple[PARAMS](
      outputs: Vector[EndpointOutput[_]],
      private[tapir] val mkParams: MkParams,
      private[tapir] val unParams: UnParams
  ) extends EndpointOutput[PARAMS] {
    override private[tapir] type ThisType[X] = EndpointOutput[X]
    override def show: String = if (outputs.isEmpty) "-" else outputs.map(_.show).mkString(" ")
    override def map[U](m: Mapping[PARAMS, U]): EndpointOutput[U] =
      MappedMultiple[PARAMS, PARAMS](this, Mapping.id).map(m)

    override def and[J, IJ](other: EndpointOutput[J])(implicit concat: ParamConcat.Aux[PARAMS, J, IJ]): EndpointOutput[IJ] =
      if (outputs.isEmpty)
        other.asInstanceOf[EndpointOutput[IJ]] // the other multiple must correspond to Unit, which is a neutral element of concatenation
      else
        other match {
          case Multiple(m, _, _) if concat.bothTuples              => Multiple.combine(outputs ++ m, concat, this, other)
          case Multiple(m, _, _) if concat.rightIsTuple            => Multiple.combine(this +: m, concat, this, other)
          case EndpointIO.Multiple(m, _, _) if concat.bothTuples   => Multiple.combine(outputs ++ m, concat, this, other)
          case EndpointIO.Multiple(m, _, _) if concat.rightIsTuple => Multiple.combine(this +: m, concat, this, other)
          case Void()                                              => this.asInstanceOf[EndpointOutput.Multiple[IJ]]
          case o if concat.leftIsTuple                             => Multiple.combine(outputs :+ o, concat, this, other)
          case o                                                   => Multiple.combine(Vector(this, o), concat, this, other)
        }
  }

  object Multiple {
    private[tapir] def combine[R, T, U](
        combinedOutputs: Vector[EndpointOutput[_]],
        concat: ParamConcat[T, U],
        left: EndpointOutput[_],
        right: EndpointOutput[_]
    ): Multiple[R] = {
      def fromOutput(o: EndpointOutput[_]): (MkParams, UnParams) = o match {
        case EndpointOutput.Multiple(_, m, u) => (m, u)
        case EndpointIO.Multiple(_, m, u)     => (m, u)
        case _                                => (MkParams.Single, UnParams.Single)
      }
      val (mkParamsLeft, unParamsLeft) = fromOutput(left)
      val (mkParamsRight, unParamsRight) = fromOutput(right)
      Multiple(combinedOutputs, combineMkParams(mkParamsLeft, mkParamsRight, concat), combineUnParams(unParamsLeft, unParamsRight, concat))
    }
  }
}

sealed trait EndpointIO[T] extends EndpointInput[T] with EndpointOutput[T] {
  private[tapir] type ThisType[X] <: EndpointInput[X] with EndpointOutput[X]

  def and[J, IJ](other: EndpointIO[J])(implicit ts: ParamConcat.Aux[T, J, IJ]): EndpointOutput[IJ]

  def show: String
}

object EndpointIO {
  sealed trait Single[I] extends EndpointIO[I] with EndpointInput.Single[I] with EndpointOutput.Single[I] {
    private[tapir] type ThisType[X] <: EndpointIO.Single[X]

    def and[J, IJ](other: EndpointIO[J])(implicit concat: ParamConcat.Aux[I, J, IJ]): EndpointIO[IJ] =
      other match {
        case s: Single[_]            => Multiple.combine(Vector(this, s), concat, this, other)
        case Multiple(outputs, _, _) => Multiple.combine(this +: outputs, concat, this, other)
      }
  }

  sealed trait Basic[I] extends Single[I] with EndpointInput.Basic[I] with EndpointOutput.Basic[I]

  case class Body[R, T](bodyType: RawBodyType[R], codec: Codec[R, T, CodecFormat], info: Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = Body[R, X]
    override private[tapir] type L = R
    override private[tapir] type CF = CodecFormat
    override private[tapir] def copyWith[U](c: Codec[R, U, CodecFormat], i: Info[U]): Body[R, U] = copy(codec = c, info = i)
    override def show: String = {
      val charset = bodyType.asInstanceOf[RawBodyType[_]] match {
        case RawBodyType.StringBody(charset) => s" (${charset.toString})"
        case _                               => ""
      }
      val format = codec.format.mediaType
      addValidatorShow(s"{body as $format$charset}", codec.validator)
    }
  }

  case class StreamBodyWrapper[S, T](wrapped: StreamingEndpointIO.Body[S, T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = StreamBodyWrapper[S, X]
    override private[tapir] type L = S
    override private[tapir] type CF = CodecFormat
    override private[tapir] def copyWith[U](c: Codec[S, U, CodecFormat], i: Info[U]): StreamBodyWrapper[S, U] = copy(wrapped.copyWith(c, i))

    override def codec: Codec[S, T, CodecFormat] = wrapped.codec
    override def info: Info[T] = wrapped.info

    override def show = s"{body as stream}"
  }

  case class FixedHeader[T](h: sttp.model.Header, codec: Codec[Unit, T, TextPlain], info: Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = FixedHeader[X]
    override private[tapir] type L = Unit
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[Unit, U, TextPlain], i: Info[U]): FixedHeader[U] = copy(codec = c, info = i)
    override def show = s"{header ${h.name}: ${h.value}}"
  }

  case class Header[T](name: String, codec: Codec[List[String], T, TextPlain], info: Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = Header[X]
    override private[tapir] type L = List[String]
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[List[String], U, TextPlain], i: Info[U]): Header[U] = copy(codec = c, info = i)
    override def show: String = addValidatorShow(s"{header $name}", codec.validator)
  }

  case class Headers[T](codec: Codec[List[sttp.model.Header], T, TextPlain], info: Info[T]) extends Basic[T] {
    override private[tapir] type ThisType[X] = Headers[X]
    override private[tapir] type L = List[sttp.model.Header]
    override private[tapir] type CF = TextPlain
    override private[tapir] def copyWith[U](c: Codec[List[sttp.model.Header], U, TextPlain], i: Info[U]): Headers[U] =
      copy(codec = c, info = i)
    override def show = s"{multiple headers}"
  }

  //

  case class MappedMultiple[PARAMS, T](io: Multiple[PARAMS], mapping: Mapping[PARAMS, T]) extends EndpointIO.Single[T] {
    override private[tapir] type ThisType[X] = MappedMultiple[PARAMS, X]
    override private[tapir] def _mapping: Mapping[_, T] = mapping
    override def show: String = io.show
    override def map[U](m: Mapping[T, U]): MappedMultiple[PARAMS, U] = copy[PARAMS, U](io, mapping.map(m))
  }

  case class Multiple[PARAMS](ios: Vector[EndpointIO[_]], private[tapir] val mkParams: MkParams, private[tapir] val unParams: UnParams)
      extends EndpointIO[PARAMS] {
    override private[tapir] type ThisType[X] = EndpointIO[X]
    override def show: String = if (ios.isEmpty) "-" else ios.map(_.show).mkString(" ")
    override def map[U](mapping: Mapping[PARAMS, U]): EndpointIO[U] =
      MappedMultiple[PARAMS, PARAMS](this, Mapping.id).map(mapping)

    override def and[J, IJ](other: EndpointInput[J])(implicit concat: ParamConcat.Aux[PARAMS, J, IJ]): EndpointInput[IJ] =
      if (ios.isEmpty)
        other.asInstanceOf[EndpointInput[IJ]] // the other multiple must correspond to Unit, which is a neutral element of concatenation
      else
        other match {
          case EndpointInput.Multiple(m, _, _) if concat.bothTuples =>
            EndpointInput.Multiple.combine((ios: Vector[EndpointInput[_]]) ++ m, concat, this, other)
          case EndpointInput.Multiple(m, _, _) if concat.rightIsTuple => EndpointInput.Multiple.combine(this +: m, concat, this, other)
          case EndpointIO.Multiple(m, _, _) if concat.bothTuples =>
            EndpointInput.Multiple.combine((ios: Vector[EndpointInput[_]]) ++ m, concat, this, other)
          case EndpointIO.Multiple(m, _, _) if concat.rightIsTuple => EndpointInput.Multiple.combine(this +: m, concat, this, other)
          case i if concat.leftIsTuple                             => EndpointInput.Multiple.combine((ios: Vector[EndpointInput[_]]) :+ i, concat, this, other)
          case i                                                   => EndpointInput.Multiple.combine(Vector(this, i), concat, this, other)
        }
    override def and[J, IJ](other: EndpointOutput[J])(implicit concat: ParamConcat.Aux[PARAMS, J, IJ]): EndpointOutput[IJ] =
      if (ios.isEmpty)
        other.asInstanceOf[EndpointOutput[IJ]] // the other multiple must correspond to Unit, which is a neutral element of concatenation
      else
        other match {
          case EndpointOutput.Multiple(m, _, _) if concat.bothTuples =>
            EndpointOutput.Multiple.combine((ios: Vector[EndpointOutput[_]]) ++ m, concat, this, other)
          case EndpointOutput.Multiple(m, _, _) if concat.rightIsTuple =>
            EndpointOutput.Multiple.combine(this +: m, concat, this, other)
          case EndpointIO.Multiple(m, _, _) if concat.bothTuples =>
            EndpointOutput.Multiple.combine((ios: Vector[EndpointOutput[_]]) ++ m, concat, this, other)
          case EndpointIO.Multiple(m, _, _) if concat.rightIsTuple =>
            EndpointOutput.Multiple.combine(this +: m, concat, this, other)
          case EndpointOutput.Void() => this.asInstanceOf[EndpointOutput.Multiple[IJ]]
          case io if concat.leftIsTuple =>
            EndpointOutput.Multiple.combine((ios: Vector[EndpointOutput[_]]) :+ io, concat, this, other)
          case io => EndpointOutput.Multiple.combine(Vector(this, io), concat, this, other)
        }
    override def and[J, IJ](other: EndpointIO[J])(implicit concat: ParamConcat.Aux[PARAMS, J, IJ]): EndpointIO[IJ] =
      if (ios.isEmpty)
        other.asInstanceOf[EndpointIO[IJ]] // the other multiple must correspond to Unit, which is a neutral element of concatenation
      else
        other match {
          case Multiple(m, _, _) if concat.bothTuples   => Multiple.combine(ios ++ m, concat, this, other)
          case Multiple(m, _, _) if concat.rightIsTuple => Multiple.combine(this +: m, concat, this, other)
          case io if concat.leftIsTuple                 => Multiple.combine(ios :+ io, concat, this, other)
          case io                                       => Multiple.combine(Vector(this, io), concat, this, other)
        }
  }

  object Multiple {
    private[tapir] def combine[R, T, U](
        combinedOutputs: Vector[EndpointIO[_]],
        concat: ParamConcat[T, U],
        left: EndpointIO[_],
        right: EndpointIO[_]
    ): Multiple[R] = {
      def fromIO(o: EndpointIO[_]): (MkParams, UnParams) = o match {
        case EndpointIO.Multiple(_, m, u) => (m, u)
        case _                            => (MkParams.Single, UnParams.Single)
      }
      val (mkParamsLeft, unParamsLeft) = fromIO(left)
      val (mkParamsRight, unParamsRight) = fromIO(right)
      Multiple(combinedOutputs, combineMkParams(mkParamsLeft, mkParamsRight, concat), combineUnParams(unParamsLeft, unParamsRight, concat))
    }
  }

  //

  case class Example[+T](value: T, name: Option[String], summary: Option[String])

  object Example {
    def of[T](t: T, name: Option[String] = None, summary: Option[String] = None): Example[T] = Example(t, name, summary)
  }

  case class Info[T](description: Option[String], examples: List[Example[T]], deprecated: Boolean) {
    def description(d: String): Info[T] = copy(description = Some(d))
    def example: Option[T] = examples.headOption.map(_.value)
    def example(t: T): Info[T] = example(Example.of(t))
    def example(example: Example[T]): Info[T] = copy(examples = examples :+ example)
    def examples(ts: List[Example[T]]): Info[T] = copy(examples = ts)
    def deprecated(d: Boolean): Info[T] = copy(deprecated = d)

    def map[U](codec: Mapping[T, U]): Info[U] =
      Info(
        description,
        examples.map(e => e.copy(value = codec.decode(e.value))).collect {
          case Example(DecodeResult.Value(ee), name, summary) => Example(ee, name, summary)
        },
        deprecated
      )
  }
  object Info {
    def empty[T]: Info[T] = Info[T](None, Nil, deprecated = false)
  }

  trait Mappable[T] {
    private[tapir] type ThisType[X]

    def map[U](mapping: Mapping[T, U]): ThisType[U]
    def map[U](f: T => U)(g: U => T): ThisType[U] = map(Mapping.from(f)(g))
    def mapDecode[U](f: T => DecodeResult[U])(g: U => T): ThisType[U] = map(Mapping.fromDecode(f)(g))
    def mapTo[COMPANION, CASE_CLASS <: Product](c: COMPANION)(implicit fc: FnComponents[COMPANION, T, CASE_CLASS]): ThisType[CASE_CLASS] = {
      map[CASE_CLASS](fc.tupled(c).apply(_))(ProductToParams(_, fc.arity).asInstanceOf[T])
    }

    def validate(v: Validator[T]): ThisType[T] = map(Mapping.id[T].validate(v))
  }

  trait HasMetadata[T] extends Mappable[T] {
    private[tapir] type L
    private[tapir] type CF <: CodecFormat

    def codec: Codec[L, T, CF]
    def info: EndpointIO.Info[T]
    private[tapir] def copyWith[U](c: Codec[L, U, CF], i: EndpointIO.Info[U]): ThisType[U]

    override def map[U](mapping: Mapping[T, U]): ThisType[U] = copyWith(codec.map(mapping), info.map(mapping))

    def schema(s: Schema[T]): ThisType[T] = copyWith(codec.schema(s), info)
    def schema(s: Option[Schema[T]]): ThisType[T] = copyWith(codec.schema(s), info)
    def modifySchema(modify: Schema[T] => Schema[T]): ThisType[T] = copyWith(codec.modifySchema(modify), info)

    def description(d: String): ThisType[T] = copyWith(codec, info.description(d))
    def example(t: T): ThisType[T] = copyWith(codec, info.example(t))
    def example(example: Example[T]): ThisType[T] = copyWith(codec, info.example(example))
    def examples(examples: List[Example[T]]): ThisType[T] = copyWith(codec, info.examples(examples))
    def deprecated(): ThisType[T] = copyWith(codec, info.deprecated(true))
  }
}

/*
Streaming body is a special kind of input, as it influences the 4th type parameter of `Endpoint`. Other inputs
(`EndpointInput`s and `EndpointIO`s aren't parametrised with the type of streams that they use (to make them simpler),
so we need to pass the streaming information directly between the streaming body input and the endpoint.

That's why the streaming body input is a separate trait, unrelated to `EndpointInput`: it can't be combined with
other inputs, and the `Endpoint.in(EndpointInput)` method can't be used to add a streaming body. Instead, there's an
overloaded variant `Endpoint.in(StreamingEndpointIO)`, which takes into account the streaming type.

Internally, the streaming body is converted into a wrapper `EndpointIO`, which "forgets" about the streaming
information. The `EndpointIO.StreamBodyWrapper` should only be used internally, not by the end user: there's no
factory method in `Tapir` which would directly create an instance of it.
 */
sealed trait StreamingEndpointIO[T, +S] extends EndpointIO.Mappable[T] with EndpointIO.HasMetadata[T] {
  private[tapir] def toEndpointIO: EndpointIO[T]
}

object StreamingEndpointIO {
  case class Body[S, T](codec: Codec[S, T, CodecFormat], info: EndpointIO.Info[T], charset: Option[Charset])
      extends StreamingEndpointIO[T, S] {
    override private[tapir] type ThisType[X] = Body[S, X]
    override private[tapir] type L = S
    override private[tapir] type CF = CodecFormat
    override private[tapir] def copyWith[U](c: Codec[S, U, CodecFormat], i: Info[U]) = copy(codec = c, info = i)

    private[tapir] override def toEndpointIO: EndpointIO.StreamBodyWrapper[S, T] = EndpointIO.StreamBodyWrapper(this)
  }
}
