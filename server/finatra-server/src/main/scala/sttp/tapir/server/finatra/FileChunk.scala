package sttp.tapir.server.finatra

import sttp.tapir.FileRange

import java.io.{FileInputStream, IOException, InputStream}

object FileChunk {

  def prepare(tapirFile: FileRange): Option[InputStream] =
    tapirFile.range.flatMap(_.startAndEnd.map(s => RangeInputStream(new FileInputStream(tapirFile.file), s._1, s._2)))
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
