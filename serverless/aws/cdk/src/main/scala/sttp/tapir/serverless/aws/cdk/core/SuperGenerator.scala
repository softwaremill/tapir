package sttp.tapir.serverless.aws.cdk.core

object SuperGenerator { // fixme rename

  private val separator = System.lineSeparator()

  def generate(resources: List[Resource]): List[String] = {
    def util(rs: List[Resource]): List[String] = {
      rs match {
        case head :: Nil  => generate(head)
        case head :: tail => generate(head) ++ List(separator) ++ util(tail)
        case _            => List.empty
      }
    }

    util(resources)
  }

  def generateV2(tree: Tree): Seq[String] = {
    tree.foldLeft(Seq.empty[String]) { case (acc, rootNode) =>
      val name = rootNode.name.raw
      val comments = rootNode.methods.map(m => s"// $m /$name")
      val rootResource = s"root${toVariableName(name)}"
      val rootApiDefinition = s"const $rootResource = api.root.addResource('$name');"
      val methods = rootNode.methods.map(m => s"$rootResource.addMethod('$m');")
      val separator = if (acc.size < (tree.size - 1) && rootNode.children.isEmpty) Seq("") else Seq.empty

      acc ++ comments ++ Seq(rootApiDefinition) ++ methods ++ separator ++ generateForNode(path = "", rootResource, rootNode)
    }
  }

  private def generateForNode(path: String, parentResourceName: String, parentNode: Node): Seq[String] = {
    val currentPath = s"$path/${parentNode.name.toString}"

    parentNode.children.foldLeft(Seq.empty[String]) { case (acc, node) =>
      val name = node.name
      val nodePath = s"$currentPath/${name.toString}"
      val comments = node.methods.map(m => s"// $m $nodePath")
      val resourceConstName = s"$parentResourceName${toVariableName(name.raw)}"
      val addResourceInvocation = s"const $resourceConstName = $parentResourceName.addResource('${name.toString}');"
      val addResourceMethods = node.methods.map(m => s"$resourceConstName.addMethod('$m');")
      acc ++ Seq("") ++ comments ++ Seq(addResourceInvocation) ++ addResourceMethods ++ generateForNode(
        currentPath,
        resourceConstName,
        node
      )
    }
  }

  private def toVariableName(name: String): String = name.replaceAll("[^a-zA-Z]", "").capitalize

  private def generate(resource: Resource): List[String] = {
    val nel = resource.method.sortBy(_.toString).toList.flatMap(m => addMethod(resource.variableName, m))
    addResource(resource.variableName, resource.dependOn, resource.path) ++ nel
  }

  private def addResource(variableName: VariableName, dependOn: String, path: String): List[String] = {
    val api = if (dependOn.isEmpty) "api.root" else dependOn
    List(s"const $variableName = $api.addResource('$path');")
  }

  private def addMethod(variableName: VariableName, method: Method): List[String] =
    List(s"${variableName.toString}.addMethod('$method');")
}
