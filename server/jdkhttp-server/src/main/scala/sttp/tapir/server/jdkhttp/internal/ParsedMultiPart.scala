package sttp.tapir.server.jdkhttp.internal

import sttp.model.Header
import sttp.tapir.Defaults.createTempFile
import sttp.tapir.TapirFile

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, FileInputStream, FileOutputStream, InputStream, OutputStream}
import scala.annotation.tailrec

case class ParsedMultiPart(headers: Map[String, Seq[String]], body: InputStream) {
  def getHeader(headerName: String): Option[String] = headers.get(headerName).flatMap(_.headOption)
  def fileItemHeaders: Seq[Header] = headers.toSeq.flatMap { case (name, values) =>
    values.map(value => Header(name, value))
  }

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
  def empty: ParsedMultiPart = new ParsedMultiPart(
    Map.empty,
    new ByteArrayInputStream(Array.empty)
  )

  sealed trait ParseState
  case object Default extends ParseState
  case object AfterBoundary extends ParseState
  case object AfterParsedBody extends ParseState
  case object AfterHeaderSpace extends ParseState

  private case class ParseData(
      currentPart: ParsedMultiPart,
      completedParts: List[ParsedMultiPart],
      parseState: ParseState
  ) {
    def done(): ParseData = this
    def changeState(state: ParseState): ParseData = this.copy(parseState = state)
    def addHeader(header: String): ParseData = this.copy(currentPart = currentPart.addHeader(header))
    def completePart(body: InputStream): ParseData = this.currentPart.getName match {
      case Some(_) =>
        this.copy(
          completedParts = completedParts :+ currentPart.copy(body = body),
          currentPart = empty,
          parseState = AfterParsedBody
        )
      case None => changeState(AfterParsedBody)
    }
  }

  private def toTruncatedInputStream(streamAndFile: PartStream, stopAt: Int): InputStream = streamAndFile match {
    case ByteStream(stream) =>
      val bytes = stream.toByteArray
      new ByteArrayInputStream(bytes.slice(0, stopAt))
    case FileStream(f, fileOutputStream) =>
      fileOutputStream.getChannel.truncate(stopAt.toLong)
      fileOutputStream.close()
      new FileInputStream(f)
  }

  private def readUntilNewline(inputStream: InputStream): Array[Byte] =
    Iterator
      .continually(inputStream.read())
      .sliding(2)
      .takeWhile(twoChars => twoChars != Seq('\r', '\n') && twoChars != Seq('\n') && !twoChars.lastOption.contains(-1))
      .map(_.head.toByte)
      .toArray

  private def readStringUntilNewline(inputStream: InputStream) = {
    val bytes = readUntilNewline(inputStream)
    new String(bytes)
  }

  def parseMultipartBody(inputStream: InputStream, boundary: String): Seq[ParsedMultiPart] = {
    val boundaryBytes = boundary.getBytes

    @tailrec
    def recursivelyParseState(state: ParseData): ParseData = state.parseState match {
      case Default =>
        val readString = readStringUntilNewline(inputStream)
        if (readString == boundary) recursivelyParseState(state.changeState(AfterBoundary))
        else state.done()
      case AfterBoundary =>
        val line = readStringUntilNewline(inputStream)
        if (line.trim.isBlank) recursivelyParseState(state.changeState(AfterHeaderSpace))
        else recursivelyParseState(state.addHeader(line))
      case AfterParsedBody =>
        val line = readStringUntilNewline(inputStream)
        if (line.trim.isBlank) state.done()
        else recursivelyParseState(state.addHeader(line).changeState(AfterBoundary))
      case AfterHeaderSpace =>
        val partInputStream = parseMultipartBodyPart(inputStream, boundaryBytes)
        recursivelyParseState(state.completePart(partInputStream))
    }

    recursivelyParseState(ParseData(empty, List.empty, Default)).completedParts
  }

  private val TempFileThreshold = 52_428_800
  private sealed trait PartStream {
    val stream: OutputStream
    def convertToFileIfThresholdMet(numReadBytes: Int): PartStream = this match {
      case ByteStream(os) if numReadBytes >= TempFileThreshold =>
        val newFile = createTempFile()
        val fileOutputStream = new FileOutputStream(newFile)
        os.writeTo(fileOutputStream)
        os.close()
        FileStream(newFile, fileOutputStream)
      case _ => this
    }
    def write(byte: Int): Unit = this.stream.write(byte)
  }
  private case class FileStream(tempFile: TapirFile, stream: FileOutputStream) extends PartStream
  private case class ByteStream(stream: ByteArrayOutputStream = new ByteArrayOutputStream()) extends PartStream

  private def parseMultipartBodyPart(is: InputStream, boundaryBytes: Array[Byte]): InputStream = {
    @tailrec
    def recursivelyParseBodyBytes(outputStream: PartStream, lastXBytes: Array[Byte], numReadBytes: Int): InputStream = {
      val currentByte = is.read()
      if (currentByte == -1) throw new IllegalArgumentException("Parsing multipart failed, ran out of bytes before finding boundary")
      val partStream = outputStream.convertToFileIfThresholdMet(numReadBytes)
      partStream.write(currentByte)

      val updatedLastXBytes =
        if (lastXBytes.length < boundaryBytes.length) lastXBytes :+ currentByte.toByte
        else lastXBytes.tail :+ currentByte.toByte

      val reachedBoundary = updatedLastXBytes.sameElements(boundaryBytes)
      if (!reachedBoundary) {
        recursivelyParseBodyBytes(partStream, updatedLastXBytes, numReadBytes + 1)
      } else {
        val boundaryIdx = updatedLastXBytes.indexOfSlice(boundaryBytes)
        readUntilNewline(is): Unit
        val bytesToRemove = updatedLastXBytes.length - boundaryIdx + 1
        val sizeWithoutBoundary = numReadBytes - bytesToRemove
        toTruncatedInputStream(partStream, sizeWithoutBoundary)
      }
    }

    recursivelyParseBodyBytes(ByteStream(), new Array[Byte](boundaryBytes.length), 0)
  }
}
