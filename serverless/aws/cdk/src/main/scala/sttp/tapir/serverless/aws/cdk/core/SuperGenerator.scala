package sttp.tapir.serverless.aws.cdk.core

import cats.data.NonEmptyList

object SuperGenerator {

  val separator = System.lineSeparator()

  def generate(resources: List[Resource]): String = {
    def util(r: List[Resource]): String = {
      r match {
        case head :: Nil  => generate(head)
        case head :: tail => generate(head) + separator + separator + util(tail)
        case _            => ""
      }
    }

    util(resources)
  }

  private def generate(resource: Resource): String = {
    val nel: NonEmptyList[String] = resource.method.sortBy(_.toString).map(m => addMethod(resource.variableName, m))
    val methods = nel.toList.mkString(separator)
    addResource(resource.variableName, resource.dependOn, resource.path) + separator + methods
  }

  //fixme tab number should be dynamic (or skipped all togheter)
  //^ just return list of lines so client may reformat this easily
  private def addResource(variableName: String, dependOn: String, path: String): String = {
    val api = if (dependOn.isEmpty) "api.root" else dependOn
    s"    const $variableName = $api.addResource('$path');"
  }

  // fixme use VN
  private def addMethod(variableName: String, method: Method): String = {
    s"    $variableName.addMethod('$method');"
  }
}
