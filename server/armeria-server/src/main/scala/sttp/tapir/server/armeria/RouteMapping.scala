package sttp.tapir.server.armeria

import com.linecorp.armeria.common.ExchangeType
import com.linecorp.armeria.server.Route
import sttp.tapir.EndpointIO.{Body, StreamBodyWrapper}
import sttp.tapir.EndpointInput.{FixedPath, PathCapture, PathsCapture}
import sttp.tapir.RawBodyType.FileBody
import sttp.tapir.internal.{RichEndpoint, RichEndpointInput, RichEndpointOutput}
import sttp.tapir._

private[armeria] object RouteMapping {

  def toRoute(e: AnyEndpoint): List[(Route, ExchangeType)] = {
    val inputs: Seq[EndpointInput.Basic[_]] = e.asVectorOfBasicInputs()

    val outputsList = e.output.asBasicOutputsList
    val requestStreaming = inputs.exists(isStreaming)
    val responseStreaming = outputsList.exists(_.exists(isStreaming))
    val exchangeType = (requestStreaming, responseStreaming) match {
      case (false, false) => ExchangeType.UNARY
      case (true, false)  => ExchangeType.REQUEST_STREAMING
      case (false, true)  => ExchangeType.RESPONSE_STREAMING
      case (true, true)   => ExchangeType.BIDI_STREAMING
    }

    val hasNoTrailingSlash = e.securityInput
      .and(e.input)
      .asVectorOfBasicInputs()
      .exists {
        case i: EndpointInput.ExtractFromRequest[_] if i.attribute(NoTrailingSlash.Attribute).getOrElse(false) => true
        case _                                                                                                 => false
      }

    toPathPatterns(inputs, hasNoTrailingSlash).map { path =>
      // Allows all HTTP method to handle invalid requests by RejectInterceptor
      val routeBuilder =
        Route
          .builder()
          .path(path)

      (routeBuilder.build(), exchangeType)
    }
  }

  private def isStreaming(output: EndpointTransput.Basic[_]): Boolean = output match {
    case StreamBodyWrapper(_) => true
    case body: Body[_, _]     =>
      body.bodyType match {
        case FileBody                        => true
        case RawBodyType.MultipartBody(_, _) => true
        case _                               => false
      }
    case _ => false
  }

  private def toPathPatterns(inputs: Seq[EndpointInput.Basic[_]], hasNoTrailingSlash: Boolean): List[String] = {
    var idxUsed = 0
    var capturePaths = false
    val fragments = inputs.collect {
      case segment: FixedPath[_] =>
        segment.show
      case PathCapture(Some(name), _, _) =>
        s"/:$name"
      case PathCapture(_, _, _) =>
        idxUsed += 1
        s"/:param$idxUsed"
      case PathsCapture(_, _) =>
        idxUsed += 1
        capturePaths = true
        s"/:*param$idxUsed"
    }
    if (fragments.isEmpty) {
      // No path should match anything
      List("prefix:/")
    } else {
      val pathPattern = fragments.mkString
      if (capturePaths) {
        List(pathPattern)
      } else {
        if (hasNoTrailingSlash) List(pathPattern)
        else {
          // endpoint.in("api") should match both '/api', '/api/'
          List(pathPattern, s"$pathPattern/")
        }
      }
    }
  }
}
