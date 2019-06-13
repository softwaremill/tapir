package tapir.client.generated

trait Language[Language] {
  def generateCode(apiName: String,
                   typeDescriptions: Set[TypeDeclaration[_]],
                   map: Set[OutcomeOfPathElement],
                   httpCaller: HttpCaller[Language]): String
}

case object Language {
  def apply[T](implicit language: Language[T]) = language
}