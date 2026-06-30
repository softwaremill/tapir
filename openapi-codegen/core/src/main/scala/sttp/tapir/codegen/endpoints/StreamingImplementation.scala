package sttp.tapir.codegen.endpoints

sealed trait StreamingImplementation
object Akka extends StreamingImplementation
case class FS2(effectType: String = "cats.effect.IO") extends StreamingImplementation
object Pekko extends StreamingImplementation
object Zio extends StreamingImplementation
