package sttp.tapir.json

object Fixtures:
  enum ColorEnum:
    case Green, Pink

  case class Response(color: ColorEnum, description: String)

  enum RichColorEnum(val code: Int):
    case Cyan extends RichColorEnum(3)
    case Magenta extends RichColorEnum(18)

  case class RichColorResponse(color: RichColorEnum)
