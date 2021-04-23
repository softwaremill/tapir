package sttp.tapir.model

abstract class SttpFile private[model] (val underlying: Any) extends SttpFileExtensions {
  def name: String
  def size: Long
}

object SttpFile extends SttpFileCompanionExtensions {}