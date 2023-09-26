package sttp.tapir.server.jdkhttp.internal

import com.sun.net.httpserver.HttpExchange
import sttp.model.Header
import java.io.{BufferedReader, InputStreamReader}

case class ParsedMultiPart(headers: Map[String, Seq[String]], body: Array[Byte]) {
  def getHeader(headerName: String): Option[String] = headers.get(headerName).flatMap(_.headOption)
  def fileItemHeaders: Seq[Header] = headers.toSeq.flatMap { case (name, values) => values.map(value => Header(name, value)) }

  def getDispositionParams: Map[String, String] = {
    val headerValue = getHeader("content-disposition")
    headerValue
      .map(
        _.split(";")
          .map(_.trim)
          .tail
          .map(_.split("="))
          .map(array => array(0) -> array(1))
          .toMap
      )
      .getOrElse(Map.empty)
  }

  def getName: Option[String] =
    headers
      .getOrElse("content-disposition", Seq.empty)
      .headOption
      .flatMap(
        _.split(";")
          .find(_.trim.startsWith("name"))
          .map(_.split("=")(1).trim)
          .map(_.replaceAll("^\"|\"$", ""))
      )

  def addHeader(l: String): ParsedMultiPart = {
    val (name, value) = l.splitAt(l.indexOf(":"))
    val headerName = name.trim.toLowerCase
    val headerValue = value.stripPrefix(":").trim
    val newHeaderEntry = (headerName -> (this.headers.getOrElse(headerName, Seq.empty) :+ headerValue))
    this.copy(headers = headers + newHeaderEntry)
  }

}

object ParsedMultiPart {
  def empty: ParsedMultiPart = new ParsedMultiPart(Map.empty, Array.empty)

  sealed trait ParseState
  case object Default extends ParseState
  case object AfterBoundary extends ParseState
  case object AfterHeaderSpace extends ParseState

  private case class ParseData(
      currentPart: ParsedMultiPart,
      completedParts: List[ParsedMultiPart],
      parseState: ParseState
  ) {
    def changeState(state: ParseState): ParseData = this.copy(parseState = state)
    def addHeader(header: String): ParseData = this.copy(currentPart = currentPart.addHeader(header))
    def addBody(body: Array[Byte]): ParseData = this.copy(currentPart = currentPart.copy(body = currentPart.body ++ body))
    def completePart(): ParseData = this.currentPart.getName match {
      case Some(_) =>
        this.copy(
          completedParts = completedParts :+ currentPart,
          currentPart = empty,
          parseState = AfterBoundary
        )
      case None => changeState(AfterBoundary)
    }
  }

  def parseMultipartBody(httpExchange: HttpExchange, boundary: String): Seq[ParsedMultiPart] = {
    val reader = new BufferedReader(new InputStreamReader(httpExchange.getRequestBody))
    val initialParseState: ParseData = ParseData(empty, List.empty, Default)
    Iterator
      .continually(reader.readLine())
      .takeWhile(_ != null)
      .foldLeft(initialParseState) { case (state, line) =>
        state.parseState match {
          case Default if line.startsWith(boundary)           => state.changeState(AfterBoundary)
          case Default                                        => state
          case AfterBoundary if line.trim.isEmpty             => state.changeState(AfterHeaderSpace)
          case AfterBoundary                                  => state.addHeader(line)
          case AfterHeaderSpace if !line.startsWith(boundary) => state.addBody(line.getBytes())
          case AfterHeaderSpace                               => state.completePart()
        }
      }
      .completedParts
  }
}
