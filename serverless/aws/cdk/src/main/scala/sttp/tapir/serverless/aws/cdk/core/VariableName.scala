package sttp.tapir.serverless.aws.cdk.core

import java.net.URLDecoder
import scala.util.Try

case class VariableName private (private val name: String, private val counter: Int = 0) {
  def changeCounter(number: Int): VariableName = new VariableName(name, number)

  override def toString: String = if (counter > 0) name + "_" + counter else name

  def raw: String = name
}

object VariableName {

  val allowedChars: List[Char] = List('-', '_')

  def apply(name: String, counter: Int = 0): VariableName = {
    // fixme deprecated
    val removeEncoded = URLDecoder.decode(name).filter(s => s.isLetterOrDigit || allowedChars.contains(s))
    val finalResult = removeEncoded.substring(0, Math.min(removeEncoded.length, 64))

    if (finalResult.isEmpty) return VariableName("v")

    Try(finalResult.toInt).toOption match {
      case Some(number) => new VariableName("p" + number.toString, counter) //fixme: this is strange, how it is gonna work?
      case None    => new VariableName(finalResult, counter)
    }
  }

  def fromSegment(segment: Segment, prefix: Option[VariableName] = None): VariableName = {
    prefix match {
      case Some(variableName) => VariableName(variableName + segment.raw.capitalize)
      case None               => VariableName(segment.raw)
    }
  }
}
