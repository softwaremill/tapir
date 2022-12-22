package sttp.tapir.server.interpreter

import sttp.tapir.{AnyEndpoint, EndpointInput}
import sttp.tapir.internal.RichEndpointInput
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint

class FilterServerEndpoints[R, F[_]](rootLayer: PathLayer[R, F]) extends (ServerRequest => List[ServerEndpoint[R, F]]) {

  /** Given a request, returns the list of server endpoints which might potentially decode successfully, taking into account the path of the
    * request.
    */
  def apply(request: ServerRequest): List[ServerEndpoint[R, F]] =
    request.pathSegments.foldLeft(rootLayer) { case (layer, segment) => layer.next(segment) }.endpoints
}

object FilterServerEndpoints {
  private sealed trait PathSegment
  private case class Exact(s: String) extends PathSegment
  private case object AnySingle extends PathSegment // ?
  private case object AnyMulti extends PathSegment // *

  private def segmentsForEndpoint(e: AnyEndpoint): List[PathSegment] = {
    val segments = e.securityInput
      .and(e.input)
      .asVectorOfBasicInputs()
      .collect {
        case EndpointInput.FixedPath(s, _, _) => Exact(s)
        case _: EndpointInput.PathCapture[_]  => AnySingle
        case _: EndpointInput.PathsCapture[_] => AnyMulti
      }
      .toList

    // no path segments mean that the endpoint matches any path
    if (segments.isEmpty) List(AnyMulti) else segments
  }

  private def createLayer[R, F[_]](segmentsToEndpoints: List[(List[PathSegment], ServerEndpoint[R, F])]): PathLayer[R, F] = {
    // first computing the distinct segments with which endpoints at this layer start
    val distinctSegments: Set[PathSegment] = segmentsToEndpoints.flatMap(_._1.headOption).toSet

    val exactSegmentToNextLayer: Map[String, PathLayer[R, F]] = distinctSegments
      // for each exact segment, creating the next layer
      .collect { case e: Exact => e }
      .map { exactSegment =>
        // computing the endpoints for the next layer: these are the endpoints which start with the given exact segment,
        // a single or multi wildcard; peeling off this initial segment, unless it is a multi wildcard (as it can capture
        // any number of path segments)
        val peeledSegmentsToEndpoints = segmentsToEndpoints.flatMap { case (segments, se) =>
          segments match {
            case head :: tail if head == exactSegment || head == AnySingle => List(tail -> se)
            case head :: _ if head == AnyMulti                             => List(segments -> se)
            case _                                                         => Nil
          }
        }

        exactSegment.s -> createLayer(peeledSegmentsToEndpoints)
      }
      .toMap

    val wildcardSegmentNextLayer = {
      // to avoid an infinite loop, if we are only left with multi wildcard path segments (capturing any number of path segments),
      // returning a "terminal" layer with these endpoints, which will match any path
      if (segmentsToEndpoints.forall { case (segments, _) => segments.headOption.contains(AnyMulti) }) {
        new PathLayer[R, F] {
          override val endpoints: List[ServerEndpoint[R, F]] = segmentsToEndpoints.map(_._2)
          override def next(pathSegment: String): PathLayer[R, F] = this
        }
      } else {
        // removing the head single wildcard path segments, and creating the next layer
        val peeledSegmentsToEndpoints = segmentsToEndpoints.flatMap { case (segments, se) =>
          segments match {
            case head :: tail if head == AnySingle => List(tail -> se)
            case head :: _ if head == AnyMulti     => List(segments -> se)
            case _                                 => Nil
          }
        }

        createLayer(peeledSegmentsToEndpoints)
      }
    }

    new PathLayer[R, F] {
      // endpoints at this layer are all for which there are no more path segments, or if the only path segment is *;
      // an empty exact segment is a special-case used to denote root paths
      override val endpoints: List[ServerEndpoint[R, F]] = segmentsToEndpoints
        .filter { case (segments, _) =>
          segments.isEmpty || segments.headOption.contains(AnyMulti) || segments.headOption.contains(Exact(""))
        }
        .map(_._2)

      override def next(pathSegment: String): PathLayer[R, F] = {
        // if there's at least one endpoint with an exact segment as the given one, looking up its layer (which also includes wildcard segments)
        // otherwise, returning the layer which handles endpoints with wildcard segments
        exactSegmentToNextLayer.getOrElse(pathSegment, wildcardSegmentNextLayer)
      }
    }
  }

  def apply[R, F[_]](serverEndpoints: List[ServerEndpoint[R, F]]): FilterServerEndpoints[R, F] = {
    val segmentsToEndpoints: List[(List[PathSegment], ServerEndpoint[R, F])] =
      serverEndpoints.map(se => segmentsForEndpoint(se.endpoint) -> se)

    new FilterServerEndpoints[R, F](createLayer(segmentsToEndpoints))
  }
}

private trait PathLayer[R, F[_]] {

  /** Endpoints at this layer, if the path is finished */
  def endpoints: List[ServerEndpoint[R, F]]

  def next(pathSegment: String): PathLayer[R, F]
}
