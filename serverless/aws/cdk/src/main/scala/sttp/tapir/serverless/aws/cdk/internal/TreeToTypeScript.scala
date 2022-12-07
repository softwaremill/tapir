package sttp.tapir.serverless.aws.cdk.internal

private[cdk] object TreeToTypeScript {
  def apply(tree: Tree): Seq[String] =
    tree.foldLeft(Seq.empty[String]) { case (acc, rootNode) =>
      val name = rootNode.name.raw
      val comments = rootNode.methods.map(m => s"// $m /$name")
      val rootResource = s"root${toVariableName(rootNode.name)}"
      val rootApiDefinition = s"const $rootResource = api.root.addResource('$name');"
      val methods = rootNode.methods.map(m => s"$rootResource.addMethod('$m');")
      val separator = if (acc.size < (tree.size - 1) && rootNode.children.isEmpty) Seq("") else Seq.empty

      acc ++ comments ++ Seq(rootApiDefinition) ++ methods ++ separator ++ generateForNode(
        path = "",
        rootResource,
        rootNode
      )
    }

  private def generateForNode(path: String, parentResourceName: String, parentNode: Node): Seq[String] = {
    val currentPath = s"$path/${parentNode.name.toString}"

    parentNode.children.foldLeft(Seq.empty[String]) { case (acc, node) =>
      val name = node.name
      val nodePath = s"$currentPath/${name.toString}"
      val comments = node.methods.map(m => s"// $m $nodePath")
      val resourceConstName = s"$parentResourceName${toVariableName(name)}"
      val addResourceInvocation = s"const $resourceConstName = $parentResourceName.addResource('${name.toString}');"
      val addResourceMethods = node.methods.map(m => s"$resourceConstName.addMethod('$m');")
      acc ++ Seq("") ++ comments ++ Seq(addResourceInvocation) ++ addResourceMethods ++ generateForNode(
        currentPath,
        resourceConstName,
        node
      )
    }
  }

  private def toVariableName(segment: Segment): String =
    segment match {
      case Segment.Fixed(name)     => name.replaceAll("[^a-zA-Z]", "").capitalize
      case Segment.Parameter(name) => s"${name.replaceAll("[^a-zA-Z]", "").capitalize}Param"
    }
}
