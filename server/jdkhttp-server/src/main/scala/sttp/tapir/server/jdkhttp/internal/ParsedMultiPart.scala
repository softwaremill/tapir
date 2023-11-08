package sttp.tapir.server.jdkhttp.internal

import sttp.model.Header
import sttp.tapir.Defaults.createTempFile
import sttp.tapir.TapirFile
import sttp.tapir.server.jdkhttp.internal.KMPMatcher.{Match, NotMatched}

import java.io.{
  BufferedOutputStream,
  ByteArrayInputStream,
  ByteArrayOutputStream,
  FileInputStream,
  FileOutputStream,
  InputStream,
  OutputStream
}
import scala.collection.mutable

case class ParsedMultiPart() {
  private var body: InputStream = new ByteArrayInputStream(Array.empty)
  private val headers: mutable.Map[String, Seq[String]] = mutable.Map.empty

  def getBody: InputStream = body
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

  def addHeader(l: String): Unit = {
    val (name, value) = l.splitAt(l.indexOf(":"))
    val headerName = name.trim.toLowerCase
    val headerValue = value.stripPrefix(":").trim
    val newHeaderEntry = headerName -> (this.headers.getOrElse(headerName, Seq.empty) :+ headerValue)
    this.headers.addOne(newHeaderEntry)
  }

  def withBody(body: InputStream): ParsedMultiPart = {
    this.body = body
    this
  }
}

object ParsedMultiPart {

  sealed trait ParseStatus {}
  private case object LookForInitialBoundary extends ParseStatus
  private case object ParsePartHeaders extends ParseStatus
  private case object ParsePartBody extends ParseStatus

  private val CRLF = Array[Byte]('\r', '\n')
  private val END_DELIMITER = Array[Byte]('-', '-')
  private val bufferSize = 8192

  private class ParseState(boundary: Array[Byte], multipartFileThresholdBytes: Long) {
    var completedParts: List[ParsedMultiPart] = List.empty
    private val buffer = new mutable.ArrayBuffer[Byte](bufferSize)
    private var done = false
    private var currentPart: ParsedMultiPart = new ParsedMultiPart()
    private var bodySize: Int = 0
    private var stream: PartStream = ByteStream()
    private var parseState: ParseStatus = LookForInitialBoundary

    private val initialBoundaryMatcher = new KMPMatcher(boundary ++ CRLF)
    private val boundaryMatcher = new KMPMatcher(CRLF ++ boundary ++ CRLF)
    private val endMatcher = new KMPMatcher(CRLF ++ boundary ++ END_DELIMITER)

    def isDone: Boolean = done

    def updateStateWith(currentByte: Int): Unit = {
      buffer.addOne(currentByte.toByte)
      this.parseState match {
        case LookForInitialBoundary => parseInitialBoundary(currentByte)
        case ParsePartHeaders       => parsePartHeaders()
        case ParsePartBody          => parsePartBody(currentByte)
      }
    }

    private def parseInitialBoundary(currentByte: Int): Unit = {
      val foundBoundary = initialBoundaryMatcher.matchByte(currentByte.toByte)
      if (foundBoundary == Match) {
        changeState(ParsePartHeaders)
      }
    }

    private def parsePartHeaders(): Unit = {
      if (buffer.endsWith(CRLF)) {
        buffer.view.map(_.toChar).mkString match {
          case headerLine if !headerLine.isBlank => addHeader(headerLine)
          case _                                 => changeState(ParsePartBody)
        }
        buffer.clear()
      } else if (buffer.length >= bufferSize) {
        throw new RuntimeException(
          s"Reached max size of $bufferSize bytes before reaching newline when parsing header."
        )
      }
    }

    private def parsePartBody(currentByte: Int): Unit = {
      bodySize += 1
      val foundFinalBoundary = endMatcher.matchByte(currentByte.toByte)
      val foundBoundary = boundaryMatcher.matchByte(currentByte.toByte)
      convertStreamToFileIfThresholdMet()

      (foundBoundary, foundFinalBoundary) match {
        case (Match, _)                                             => handleEndOfBody(boundaryMatcher.getDelimiter.length)
        case (_, Match)                                             => done = true; handleEndOfBody(endMatcher.getDelimiter.length)
        case _ if endMatcher.noMatches && boundaryMatcher.noMatches => writeAllBytes()
        case (NotMatched(x), NotMatched(y))                         => writeUnmatchedBytes(Math.min(x, y))
      }
    }

    def writeAllBytes(): Unit = {
      buffer.foreach(byte => stream.underlying.write(byte.toInt))
      buffer.clear()
    }

    def handleEndOfBody(delimiterLength: Int): Unit = {
      val bytesToWrite = buffer.view.slice(0, buffer.length - delimiterLength)
      bytesToWrite.foreach(byte => stream.underlying.write(byte.toInt))
      val bodyInputStream = stream match {
        case FileStream(tempFile, stream) => stream.close(); new FileInputStream(tempFile)
        case ByteStream(stream)           => new ByteArrayInputStream(stream.toByteArray)
      }
      completePart(bodyInputStream)
      stream = ByteStream()
      bodySize = 0
    }

    def writeUnmatchedBytes(unmatchedBytes: Int): Unit = {
      val bytesToWrite = buffer.view.slice(0, unmatchedBytes)
      bytesToWrite.foreach(byte => stream.underlying.write(byte.toInt))
      buffer.dropInPlace(unmatchedBytes)
    }

    private def changeState(state: ParseStatus): Unit = {
      buffer.clear()
      this.parseState = state
    }
    private def addHeader(header: String): Unit = this.currentPart.addHeader(header)
    private def completePart(body: InputStream): Unit = {
      this.currentPart.getName match {
        case Some(_) =>
          this.completedParts = completedParts :+ currentPart.withBody(body)
          this.currentPart = new ParsedMultiPart()
        case None =>
      }
      this.changeState(ParsePartHeaders)
    }

    private sealed trait PartStream { val underlying: OutputStream }
    private case class FileStream(tempFile: TapirFile, underlying: BufferedOutputStream) extends PartStream
    private case class ByteStream(underlying: ByteArrayOutputStream = new ByteArrayOutputStream()) extends PartStream
    private def convertStreamToFileIfThresholdMet(): Unit = stream match {
      case ByteStream(os) if bodySize >= multipartFileThresholdBytes =>
        val newFile = createTempFile()
        val fileOutputStream = new FileOutputStream(newFile)
        os.writeTo(fileOutputStream)
        os.close()
        stream = FileStream(newFile, new BufferedOutputStream(fileOutputStream))
      case _ =>
    }
  }

  def parseMultipartBody(inputStream: InputStream, boundary: String, multipartFileThresholdBytes: Long): Seq[ParsedMultiPart] = {
    val boundaryBytes = boundary.getBytes
    val state = new ParseState(boundaryBytes, multipartFileThresholdBytes)

    while (!state.isDone) {
      val currentByte = inputStream.read()
      if (currentByte == -1)
        throw new RuntimeException("Parsing multipart failed, ran out of bytes before finding boundary")
      state.updateStateWith(currentByte)
    }

    state.completedParts
  }
}
