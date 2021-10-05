package sttp.tapir.internal

import sttp.tapir.FileRange

import java.io.{FileInputStream, IOException, InputStream}

object FileChunk {

  def prepare(tapirFile: FileRange): Option[InputStream] = {
    tapirFile.range match {
      case Some(range) =>
        Some(RangeInputStream(new FileInputStream(tapirFile.file), range.start, range.end + 1))
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
    if (remaining >= 0) parent.read() else {
      if (!closed) {
        close()
        closed = true
      }
      -1
    }
  }

  @Override
  override def close(): Unit = parent.close()
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