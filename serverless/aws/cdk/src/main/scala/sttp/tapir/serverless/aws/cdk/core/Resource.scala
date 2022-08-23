package sttp.tapir.serverless.aws.cdk.core

import cats.data.NonEmptyList

private[core] case class Resource(
    variableName: String,
    path: String,
    method: NonEmptyList[Method],
    dependOn: String
)

sealed trait Method

case object GET extends Method

case object POST extends Method

case object PATCH extends Method

case object PUT extends Method

case object DELETE extends Method

object Method { // fixme move out to dedicated file
  implicit val userOrdering: Ordering[Method] = Ordering.by[Method, Int] {
    case GET => 0
    case POST => 1
    case PUT => 2
    case PATCH => 3
    case DELETE => 4
  }

  def apply(method: String): Option[Method] = method match {
    case "GET"    => Some(GET)
    case "POST"   => Some(POST)
    case "DELETE" => Some(DELETE)
    case "PATCH"  => Some(PATCH)
    case "PUT"    => Some(PUT)
    case _        => None
  }
}


private[core] object Resource {

  type Resources = List[Resource]

  case class Container(resources: Resources = List.empty, variables: Map[String, Int] = Map.empty) {
    def enrich(r: Resources, v: Map[String, Int] = Map.empty): Container = Container(resources ++ r, variables ++ v)
  }

  // fixme rename
  def generate(tree: List[Node]): Resources = {
    helper(tree).resources.sortWith((a, b) => a.variableName < b.variableName)
  }

  private def helper(
      tree: List[Node],
      root: Option[VariableName] = None,
      prefix: List[Segment] = List.empty,
      dependOn: Option[VariableName] = None,
      variables: Map[String, Int] = Map.empty
  ): Container = tree.foldLeft(Container(List.empty, variables)) { (c: Container, node: Node) =>
    {
      val rawVariableName = VariableName.fromSegment(node.name, root)
      val composedVariables = c.variables ++ variables
      val counter = composedVariables.getOrElse(rawVariableName.raw, 0)
      val suffixed = rawVariableName.changeCounter(counter)
      val newPrefix: List[Segment] = prefix :+ node.name

      NonEmptyList.fromList(node.methods.distinct) match {
        case Some(methods) => {
          val updatedVariables = composedVariables + (rawVariableName.raw -> (counter + 1))
          val resources = helper(node.content, Some(rawVariableName), List.empty, Some(rawVariableName), updatedVariables)

          val resource =
            Resource(
              suffixed.toString,
              newPrefix.mkString("/"),
              methods,
              dependOn.getOrElse("").toString
            )
          resources.enrich(resource +: c.resources, updatedVariables)
        }
        case None => {
          // skip non reachable parts
          val resources = helper(node.content, Some(suffixed), newPrefix, dependOn, composedVariables)
          resources.enrich(c.resources)
        }
      }
    }
  }
}
