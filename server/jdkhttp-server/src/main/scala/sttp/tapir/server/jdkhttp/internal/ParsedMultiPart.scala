package sttp.tapir.server.jdkhttp.internal

import sttp.model.Header
import sttp.tapir.Defaults.createTempFile
import sttp.tapir.TapirFile

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
  private case object LookForEndOrNewLine extends ParseStatus
  private val CRLF = Array[Byte]('\r', '\n')

  private class ParseState(boundaryBytes: Array[Byte], multipartFileThresholdBytes: Long) {
    var completedParts: List[ParsedMultiPart] = List.empty
    private val prefixedBoundaryBytes = CRLF ++ boundaryBytes
    private val bufferSize = 8192
    private val circularBuffer = new CircularBuffer(bufferSize)
    private var done = false
    private var currentPart: ParsedMultiPart = new ParsedMultiPart()
    private var numMatchedBoundaryChars = 0
    private var bodySize: Int = 0
    private var stream: PartStream = ByteStream()
    private var parseState: ParseStatus = LookForInitialBoundary

    def isDone: Boolean = done

    def updateStateWith(currentByte: Int): Unit = {
      circularBuffer.addByte(currentByte.toByte)
      this.parseState match {
        case LookForInitialBoundary => parseInitialBoundary(currentByte)
        case ParsePartHeaders       => parsePartHeaders()
        case ParsePartBody          => parsePartBody(currentByte)
        case LookForEndOrNewLine    => lookForEndOrNewLine()
      }
    }

    private def lookForEndOrNewLine(): Unit = {
      if (circularBuffer.endsWith(CRLF)) changeState(ParsePartHeaders)
      else if (circularBuffer.endsWith(Array('-', '-'))) done = true
    }

    private def parseInitialBoundary(currentByte: Int): Unit = {
      val foundBoundary = lookForBoundary(currentByte, boundaryBytes)
      if (foundBoundary) {
        changeState(LookForEndOrNewLine)
      }
    }

    private def parsePartHeaders(): Unit = {
      if (circularBuffer.endsWith(CRLF)) {
        circularBuffer.getString match {
          case "--\r\n"                          => done = true
          case headerLine if !headerLine.isBlank => addHeader(headerLine)
          case _                                 => changeState(ParsePartBody)
        }
        circularBuffer.reset()
      } else if (circularBuffer.isFull) {
        throw new RuntimeException(
          s"Reached max size of $bufferSize bytes before reaching newline when parsing header."
        )
      }
    }

    private def parsePartBody(currentByte: Int): Unit = {
      bodySize += 1
      val foundBoundary = lookForBoundary(currentByte, prefixedBoundaryBytes)
      convertStreamToFileIfThresholdMet()
      if (foundBoundary) {
        val bytes = circularBuffer.getBytes
        val bytesToWrite = bytes.slice(0, bytes.length - prefixedBoundaryBytes.length)
        bytesToWrite.foreach(byte => stream.underlying.write(byte.toInt))
        val bodyInputStream = stream match {
          case FileStream(tempFile, stream) => stream.close(); new FileInputStream(tempFile)
          case ByteStream(stream)           => new ByteArrayInputStream(stream.toByteArray)
        }
        completePart(bodyInputStream)
        stream = ByteStream()
        bodySize = 0
      } else if (numMatchedBoundaryChars == 0) {
        circularBuffer.getBytes.foreach(byte => stream.underlying.write(byte.toInt))
        circularBuffer.reset()
      }
    }

    private def lookForBoundary(currentByte: Int, boundary: Array[Byte]): Boolean = {
      if (currentByte == boundary(numMatchedBoundaryChars)) {
        numMatchedBoundaryChars += 1
        if (numMatchedBoundaryChars == boundary.length) {
          numMatchedBoundaryChars = 0
          true
        } else false
      } else {
        numMatchedBoundaryChars = 0
        false
      }
    }

    private def changeState(state: ParseStatus): Unit = {
      circularBuffer.reset()
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
      this.changeState(LookForEndOrNewLine)
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
