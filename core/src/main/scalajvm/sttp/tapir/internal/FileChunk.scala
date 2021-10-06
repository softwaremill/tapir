package sttp.tapir.internal

import sttp.tapir.FileRange

import java.io.{FileInputStream, IOException, InputStream}

object FileChunk {

  def prepare(tapirFile: FileRange): Option[InputStream] = {
    tapirFile.range match {
      case Some(range) =>
        (range.start, range.end) match {
          case (Some(start), Some(end)) => Some(RangeInputStream(new FileInputStream(tapirFile.file), start, end + 1))
          case (Some(start), None)      => Some(RangeInputStream(new FileInputStream(tapirFile.file), start, range.fileSize + 1))
          case (None, Some(end))        => Some(RangeInputStream(new FileInputStream(tapirFile.file), range.fileSize - end, range.fileSize))
          case _                        => None
        }
      case None => None
    }
  }
}

class RangeInputStream extends InputStream {

  private var parent: InputStream = _
  private var remaining = 0L
  private var closed = false

  @Override
  override def read(): Int = {
    remaining -= 1
    if (remaining >= 0) parent.read()
    else {
      close()
      -1
    }
  }

  @Override
  override def close(): Unit = if (!closed) {
    parent.close()
    closed = true
  }
}

object RangeInputStream {

  def apply(_parent: InputStream, start: Long, end: Long): RangeInputStream = {
    if (_parent.skip(start) < start) throw new IOException("Unable to skip leading bytes")

    val stream = new RangeInputStream
    stream.parent = _parent
    stream.remaining = end - start
    stream
  }

}
