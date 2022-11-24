package sttp.tapir.serverless.aws.cdk.core

import cats.data.NonEmptyList

private[core] case class Resource(
    variableName: VariableName,
    path: String,
    method: NonEmptyList[Method],
    dependOn: String
)

private[core] object Resource {

  type Resources = List[Resource]

  case class Container(resources: Resources = List.empty, variables: Map[String, Int] = Map.empty) {
    def enrich(r: Resources, v: Map[String, Int] = Map.empty): Container = Container(resources ++ r, variables ++ v)
  }

  // fixme rename
  def generate(tree: List[Node]): Resources = {
    helper(tree).resources.sortWith((a, b) => a.variableName.toString < b.variableName.toString)
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
          val resources = helper(node.children, Some(rawVariableName), List.empty, Some(rawVariableName), updatedVariables)

          val resource =
            Resource(
              suffixed,
              newPrefix.mkString("/"),
              methods,
              dependOn.getOrElse("").toString
            )
          resources.enrich(resource +: c.resources, updatedVariables)
        }
        case None => {
          // skip non reachable parts
          val resources = helper(node.children, Some(suffixed), newPrefix, dependOn, composedVariables)
          resources.enrich(c.resources)
        }
      }
    }
  }
}
