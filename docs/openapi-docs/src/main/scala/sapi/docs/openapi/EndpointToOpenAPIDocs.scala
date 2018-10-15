package sapi.docs.openapi

import sapi._
import sapi.openapi.OpenAPI.ReferenceOr
import sapi.openapi._
import sapi.{Endpoint, Id}

class EndpointToOpenAPIDocs {
  def toOpenAPI(title: String, version: String, es: Seq[Endpoint[Id, _, _]]): OpenAPI = {
    OpenAPI(
      info = Info(title, None, None, version),
      server = Nil,
      paths = paths(es),
      components = components(es)
    )
  }

  private def paths(es: Seq[Endpoint[Id, _, _]]): Map[String, PathItem] = {
    es.map(pathItem).groupBy(_._1).mapValues(_.map(_._2).reduce(mergePathItems))
  }

  private def pathItem(e: Endpoint[Id, _, _]): (String, PathItem) = {
    import Method._

    val pathComponents = e.input.inputs.toList.collect {
      case EndpointInput.PathCapture(name, _, _, _) => s"{$name}"
      case EndpointInput.PathSegment(s)             => s
    }
    // TODO parametrize the class with customizable id generation
    val defaultId = s"${pathComponents.mkString("-")}-${e.method.m.toLowerCase}"

    val pathItem = PathItem(
      None,
      None,
      get = if (e.method == GET) Some(operation(defaultId, e)) else None,
      put = if (e.method == PUT) Some(operation(defaultId, e)) else None,
      post = if (e.method == POST) Some(operation(defaultId, e)) else None,
      delete = if (e.method == DELETE) Some(operation(defaultId, e)) else None,
      options = if (e.method == OPTIONS) Some(operation(defaultId, e)) else None,
      head = if (e.method == HEAD) Some(operation(defaultId, e)) else None,
      patch = if (e.method == PATCH) Some(operation(defaultId, e)) else None,
      trace = if (e.method == TRACE) Some(operation(defaultId, e)) else None,
      servers = Nil,
      parameters = Nil
    )

    (pathComponents.mkString("/"), pathItem)
  }

  private def mergePathItems(p1: PathItem, p2: PathItem): PathItem = {
    PathItem(
      None,
      None,
      get = p1.get.orElse(p2.get),
      put = p1.put.orElse(p2.put),
      post = p1.post.orElse(p2.post),
      delete = p1.delete.orElse(p2.delete),
      options = p1.options.orElse(p2.options),
      head = p1.head.orElse(p2.head),
      patch = p1.patch.orElse(p2.patch),
      trace = p1.trace.orElse(p2.trace),
      servers = Nil,
      parameters = Nil
    )
  }

  private def operation(defaultId: String, e: Endpoint[Id, _, _]): Operation = {

    val parameters = e.input.inputs.collect {
      case EndpointInput.Query(n, tm, d, ex) =>
        Parameter(n,
                  ParameterIn.Query,
                  d,
                  Some(!tm.isOptional),
                  None,
                  None,
                  None,
                  None,
                  None,
                  None,
                  ex.flatMap(exampleValue(tm, _)),
                  Map(),
                  Map())
      case EndpointInput.PathCapture(n, tm, d, ex) =>
        Parameter(n, ParameterIn.Path, d, Some(true), None, None, None, None, None, None, ex.flatMap(exampleValue(tm, _)), Map(), Map())
    }

    val responses: Map[ResponsesKey, ReferenceOr[Response]] = Map(
      ResponsesCodeKey(200) -> Right(Response(endpoint.okResponseDescription.getOrElse(""), Map.empty, Map.empty)),
      ResponsesDefaultKey -> Right(Response(endpoint.errorResponseDescription.getOrElse(""), Map.empty, Map.empty)),
    )

    Operation(e.tags, e.summary, e.description, defaultId, parameters.toList.map(Right(_)), None, responses, deprecated = false, Nil)
  }

  private def exampleValue[T](tm: TypeMapper[T], e: T): Option[ExampleValue] = tm.toOptionalString(e).map(ExampleValue) // TODO

  private def components(es: Seq[Endpoint[Id, _, _]]): Components = Components(Map())
}
