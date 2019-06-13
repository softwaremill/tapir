package tapir.client.generated.language
import tapir.client.generated._

trait Scala

case object Scala extends Scala {
  implicit val language = new Language[Scala] {
    override def generateCode(apiName: String,
                              typeDescriptions: Set[TypeDeclaration[_]],
                              outcomeMap: Set[OutcomeOfPathElement],
                              httpCaller: HttpCaller[Scala]): String = {
      val innerCode = generateCode(outcomeMap, httpCaller, 1).indent(1)
      val typeDescriptionsCode = generateTypeDescriptionsCode(typeDescriptions).indent(1)

      s"""${httpCaller.imports}
         |
         |object $apiName {
         |$typeDescriptionsCode
         |}
         |
         |class $apiName(basePath: String) {
         |  import $apiName._
         |
         |$innerCode
         |}
         |""".stripMargin
    }

    private def generateTypeDescriptionsCode(typeDescriptions: Set[TypeDeclaration[_]]): String = {
      typeDescriptions
        .map { typeDescription =>
          val commaSeparatedFieldDeclaration =
            typeDescription.fields
              .map(field => s"${field.fieldName}: ${field.fieldTypeName}")
              .mkString(",")
          s"case class ${typeDescription.name}($commaSeparatedFieldDeclaration)"
        }
        .mkString("\n")
    }

    private def generateCode(
        outcome: Set[OutcomeOfPathElement],
        httpCaller: HttpCaller[Scala],
        indentationLevel: Int
    ): String = {
      outcome
        .map {
          case Parameter(name, paramTypeName, impl) =>
            val innerResult = generateCode(impl, httpCaller, indentationLevel + 1)
            s"""class $name(basePath: String) {
               |$innerResult
               |}
               |def apply($name: $paramTypeName) =
               |  new $name(basePath + "/" + $name)
               |""".stripMargin.indent(indentationLevel)
          case Function(name, impl) =>
            val innerResult = generateCode(impl, httpCaller, indentationLevel + 1)
            s"""class $name(basePath: String) {
               |$innerResult
               |}
               |def $name() =
               |  new $name(basePath + "/" + $name)
               |""".stripMargin.indent(indentationLevel)
          case httpCall @ HttpCall(method, payloadOpt, errorTypeDescription, responseTypeDescription) =>
            httpCaller.httpCall(httpCall, Map.empty).indent(indentationLevel)
        }
        .foldLeft("") {
          case (str, "") => str
          case ("", str) => str
          case (accStr, str) =>
            accStr + "\n" + str
        }
    }

    private implicit class StringOps(string: String) {
      def indent(level: Int): String = {
        def repeat(char: Char, times: Int): String = {
          (0 until times).map(_ => char).mkString
        }
        string.lines.map(repeat(' ', level) + _).mkString("\n")
      }
    }
  }
}
