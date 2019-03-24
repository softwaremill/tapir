package tapir.client.sttp

import java.io.{BufferedOutputStream, ByteArrayInputStream, FileOutputStream}
import java.nio.ByteBuffer

import com.softwaremill.sttp.{Method => SttpMethod, _}
import tapir.Codec.PlainCodec
import tapir.internal._
import tapir.typelevel.ParamsAsArgs
import tapir._
import tapir.model.{MultiQueryParams, Part, Method}

class EndpointToSttpClient(clientOptions: SttpClientOptions) {
  // don't look. The code is really, really ugly.

  def toSttpRequest[I, E, O, S](e: Endpoint[I, E, O, S], baseUri: Uri)(
      implicit paramsAsArgs: ParamsAsArgs[I]): paramsAsArgs.FN[Request[Either[E, O], S]] = {
    paramsAsArgs.toFn(params => {
      val baseReq = sttp
        .response(ignore)
        .mapResponse(Right(_): Either[Any, Any])

      val (uri, req) = setInputParams(e.input.asVectorOfSingleInputs, params, paramsAsArgs, 0, baseUri, baseReq)

      var req2 = req.copy[Id, Either[Any, Any], Any](method = SttpMethod(e.input.method.getOrElse(Method.GET).m), uri = uri)

      if (e.output.asVectorOfSingleOutputs.nonEmpty || e.errorOutput.asVectorOfSingleOutputs.nonEmpty) {
        // by default, reading the body as specified by the output, and optionally adjusting to the error output
        // if there's no body in the output, reading the body as specified by the error output
        // otherwise, ignoring
        val outputBodyType = e.output.bodyType
        val errorOutputBodyType = e.errorOutput.bodyType
        val baseResponseAs1 = outputBodyType
          .orElse(errorOutputBodyType)
          .map {
            case StringValueType(charset) => asString(charset.name())
            case ByteArrayValueType       => asByteArray
            case ByteBufferValueType      => asByteArray.map(ByteBuffer.wrap)
            case InputStreamValueType     => asByteArray.map(new ByteArrayInputStream(_))
            case FileValueType =>
              asFile(clientOptions.createFile(), overwrite = true) // TODO: use factory ResponseMetadata => File once available
            case MultipartValueType(_, _) => throw new IllegalArgumentException("Multipart bodies aren't supported in responses")
          }
          .getOrElse(ignore)

        val baseResponseAs2 = if (bodyIsStream(e.output)) asStream[Any] else baseResponseAs1

        val responseAs = baseResponseAs2.mapWithMetadata {
          (body, meta) =>
            val outputs = if (meta.isSuccess) e.output.asVectorOfSingleOutputs else e.errorOutput.asVectorOfSingleOutputs

            // the body type of the success output takes priority; that's why it might not match
            val adjustedBody =
              if (meta.isSuccess || outputBodyType.isEmpty || outputBodyType == errorOutputBodyType) body
              else errorOutputBodyType.map(adjustBody(body, _)).getOrElse(body)

            val params = getOutputParams(outputs, adjustedBody, meta)
            if (meta.isSuccess) Right(params) else Left(params)
        }

        req2 = req2.response(responseAs.asInstanceOf[ResponseAs[Either[Any, Any], S]]).parseResponseIf(_ => true)
      }

      req2.asInstanceOf[Request[Either[E, O], S]]
    })
  }

  private def getOutputParams(outputs: Vector[EndpointOutput.Single[_]], body: Any, meta: ResponseMetadata): Any = {
    val values = outputs
      .map {
        case EndpointIO.Body(codec, _) =>
          val so = if (codec.meta.isOptional && body == "") None else Some(body)
          getOrThrow(codec.decode(so))

        case EndpointIO.StreamBodyWrapper(_) =>
          body

        case EndpointIO.Header(name, codec, _) =>
          getOrThrow(codec.decode(meta.headers(name).toList))

        case EndpointIO.Headers(_) =>
          meta.headers

        case EndpointIO.Mapped(wrapped, f, _, _) =>
          f.asInstanceOf[Any => Any].apply(getOutputParams(wrapped.asVectorOfSingleOutputs, body, meta))

        case EndpointOutput.StatusCode() =>
          meta.code

        case EndpointOutput.StatusFrom(wrapped, _, _, _) =>
          getOutputParams(wrapped.asVectorOfSingleOutputs, body, meta)

        case EndpointOutput.Mapped(wrapped, f, _, _) =>
          f.asInstanceOf[Any => Any].apply(getOutputParams(wrapped.asVectorOfSingleOutputs, body, meta))
      }

    SeqToParams(values)
  }

  private type PartialAnyRequest = PartialRequest[Either[Any, Any], Any]

  private def setInputParams[I](inputs: Vector[EndpointInput.Single[_]],
                                params: I,
                                paramsAsArgs: ParamsAsArgs[I],
                                paramIndex: Int,
                                uri: Uri,
                                req: PartialAnyRequest): (Uri, PartialAnyRequest) = {

    def handleMapped[II, T](wrapped: EndpointInput[II],
                            g: T => II,
                            wrappedParamsAsArgs: ParamsAsArgs[II],
                            tail: Vector[EndpointInput.Single[_]]): (Uri, PartialAnyRequest) = {
      val (uri2, req2) = setInputParams(
        wrapped.asVectorOfSingleInputs,
        g(paramsAsArgs.paramAt(params, paramIndex).asInstanceOf[T]),
        wrappedParamsAsArgs,
        0,
        uri,
        req
      )

      setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri2, req2)
    }

    inputs match {
      case Vector() => (uri, req)
      case EndpointInput.RequestMethod(_) +: tail =>
        setInputParams(tail, params, paramsAsArgs, paramIndex, uri, req)
      case EndpointInput.PathSegment(p) +: tail =>
        setInputParams(tail, params, paramsAsArgs, paramIndex, uri.copy(path = uri.path :+ p), req)
      case EndpointInput.PathCapture(codec, _, _) +: tail =>
        val v = codec.asInstanceOf[PlainCodec[Any]].encode(paramsAsArgs.paramAt(params, paramIndex): Any)
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri.copy(path = uri.path :+ v), req)
      case EndpointInput.PathsCapture(_) +: tail =>
        val ps = paramsAsArgs.paramAt(params, paramIndex).asInstanceOf[Seq[String]]
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri.copy(path = uri.path ++ ps), req)
      case EndpointInput.Query(name, codec, _) +: tail =>
        val uri2 = codec
          .encode(paramsAsArgs.paramAt(params, paramIndex))
          .foldLeft(uri) { case (u, v) => u.param(name, v) }
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri2, req)
      case EndpointInput.Cookie(name, codec, _) +: tail =>
        val req2 = codec
          .encode(paramsAsArgs.paramAt(params, paramIndex))
          .foldLeft(req) { case (r, v) => r.cookie(name, v) }
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri, req2)
      case EndpointInput.QueryParams(_) +: tail =>
        val mqp = paramsAsArgs.paramAt(params, paramIndex).asInstanceOf[MultiQueryParams]
        val uri2 = uri.params(mqp.toSeq: _*)
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri2, req)
      case EndpointIO.Body(codec, _) +: tail =>
        val req2 = setBody(paramsAsArgs.paramAt(params, paramIndex), codec, req)
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri, req2)
      case EndpointIO.StreamBodyWrapper(_) +: tail =>
        val req2 = req.streamBody(paramsAsArgs.paramAt(params, paramIndex))
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri, req2)
      case EndpointIO.Header(name, codec, _) +: tail =>
        val req2 = codec
          .encode(paramsAsArgs.paramAt(params, paramIndex))
          .foldLeft(req) { case (r, v) => r.header(name, v) }
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri, req2)
      case EndpointIO.Headers(_) +: tail =>
        val headers = paramsAsArgs.paramAt(params, paramIndex).asInstanceOf[Seq[(String, String)]]
        val req2 = headers.foldLeft(req) { case (r, (k, v)) => r.header(k, v) }
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri, req2)
      case EndpointInput.ExtractFromRequest(_) +: tail =>
        // ignoring
        setInputParams(tail, params, paramsAsArgs, paramIndex + 1, uri, req)
      case (a: EndpointInput.Auth[_]) +: tail =>
        setInputParams(a.input +: tail, params, paramsAsArgs, paramIndex, uri, req)
      case EndpointInput.Mapped(wrapped, _, g, wrappedParamsAsArgs) +: tail =>
        handleMapped(wrapped, g, wrappedParamsAsArgs, tail)
      case EndpointIO.Mapped(wrapped, _, g, wrappedParamsAsArgs) +: tail =>
        handleMapped(wrapped, g, wrappedParamsAsArgs, tail)
    }
  }

  private def setBody[T, M <: MediaType, R](v: T, codec: CodecForOptional[T, M, R], req: PartialAnyRequest): PartialAnyRequest = {
    codec
      .encode(v)
      .map { t =>
        codec.meta.rawValueType match {
          case StringValueType(charset) => req.body(t, charset.name())
          case ByteArrayValueType       => req.body(t)
          case ByteBufferValueType      => req.body(t)
          case InputStreamValueType     => req.body(t)
          case FileValueType            => req.body(t)
          case mvt: MultipartValueType =>
            val parts: Seq[Multipart] = (t: Seq[RawPart]).flatMap { p =>
              mvt.partCodecMeta(p.name).map { codec =>
                val sttpPart1 = partToSttpPart(p.asInstanceOf[Part[Any]], codec.asInstanceOf[CodecMeta[_, Any]])
                val sttpPart2 = p.headers.foldLeft(sttpPart1) { case (sp, (hk, hv)) => sp.header(hk, hv) }
                p.fileName.map(sttpPart2.fileName).getOrElse(sttpPart2)
              }
            }

            req.multipartBody(parts.toList)
        }
      }
      .getOrElse(req)
  }

  private def partToSttpPart[R](p: Part[R], codecMeta: CodecMeta[_, R]): Multipart = codecMeta.rawValueType match {
    case StringValueType(charset) => multipart(p.name, p.body, charset.toString)
    case ByteArrayValueType       => multipart(p.name, p.body)
    case ByteBufferValueType      => multipart(p.name, p.body)
    case InputStreamValueType     => multipart(p.name, p.body)
    case FileValueType            => multipartFile(p.name, p.body)
    case MultipartValueType(_, _) => throw new IllegalArgumentException("Nested multipart bodies aren't supported")
  }

  private def bodyIsStream[I](out: EndpointOutput[I]): Boolean = {
    out match {
      case _: EndpointIO.StreamBodyWrapper[_, _]   => true
      case EndpointIO.Multiple(inputs)             => inputs.exists(i => bodyIsStream(i))
      case EndpointOutput.Multiple(inputs)         => inputs.exists(i => bodyIsStream(i))
      case EndpointIO.Mapped(wrapped, _, _, _)     => bodyIsStream(wrapped)
      case EndpointOutput.Mapped(wrapped, _, _, _) => bodyIsStream(wrapped)
      case _                                       => false
    }
  }

  // TODO: rework
  private def adjustBody[R](b: Any, bodyType: RawValueType[R]): R = {
    val asByteArray = b match {
      case s: String      => s.getBytes
      case b: Array[Byte] => b
    }

    bodyType match {
      case StringValueType(charset) => new String(asByteArray, charset)
      case ByteArrayValueType       => asByteArray
      case ByteBufferValueType      => ByteBuffer.wrap(asByteArray)
      case InputStreamValueType     => new ByteArrayInputStream(asByteArray)
      case FileValueType =>
        val f = clientOptions.createFile()
        val target = new BufferedOutputStream(new FileOutputStream(f))
        val bytes = asByteArray
        try target.write(bytes, 0, bytes.length - 1)
        finally target.close()
        f
      case MultipartValueType(_, _) => throw new IllegalArgumentException("Multipart bodies aren't supported in responses")
    }
  }

  private def getOrThrow[T](dr: DecodeResult[T]): T = dr match {
    case DecodeResult.Value(v)    => v
    case DecodeResult.Error(o, e) => throw new IllegalArgumentException(s"Cannot decode from $o", e)
    case f                        => throw new IllegalArgumentException(s"Cannot decode: $f")
  }
}
