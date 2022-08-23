package sttp.tapir.serverless.aws.cdk.core

object SuperGenerator { //fixme rename

  val separator = System.lineSeparator()

  def generate(resources: List[Resource]): List[String] = {
    def util(r: List[Resource]): List[String] = {
      r match {
        case head :: Nil  => generate(head)
        case head :: tail => generate(head) ++ List(separator) ++ util(tail)
        case _            => List.empty
      }
    }

    util(resources)
  }

  private def generate(resource: Resource): List[String] = {
    val nel = resource.method.sortBy(_.toString).toList.flatMap(m => addMethod(resource.variableName, m))
    addResource(resource.variableName, resource.dependOn, resource.path) ++ nel
  }

  private def addResource(variableName: String, dependOn: String, path: String): List[String] = {
    val api = if (dependOn.isEmpty) "api.root" else dependOn
    List(s"const $variableName = $api.addResource('$path');")
  }

  // fixme use VN
  private def addMethod(variableName: String, method: Method): List[String] =
    List(s"$variableName.addMethod('$method');")
}
