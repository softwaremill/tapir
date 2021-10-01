package sttp.tapir.internal

import sttp.tapir.FileRange

import java.io.{FileInputStream, IOException, InputStream}

object FileChunk {

  def prepare(tapirFile: FileRange): Option[InputStream] = {
    tapirFile.range match {
      case Some(range) =>
        (range.start, range.end) match {
          case (Some(start), Some(end)) =>
            val stream = RangeInputStream(new FileInputStream(tapirFile.toPath.toFile), start, end)
            Some(stream)
          case _ => None
        }
      case None => None
    }
  }
}

class RangeInputStream extends InputStream {

  private var parent: InputStream = _
  private var remaining = 0L

  @Override
  override def read(): Int = {
    remaining -= 1
    if (remaining >= 0) parent.read() else -1
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