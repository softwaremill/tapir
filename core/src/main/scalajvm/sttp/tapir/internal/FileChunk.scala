package sttp.tapir.internal

import sttp.tapir.RangeValue

import java.io.RandomAccessFile

object FileChunk {

  def prepare(tapirFile: TapirFile, range: RangeValue): Array[Byte] = {
    val raf = new RandomAccessFile(tapirFile.toFile, "r")
    raf.seek(range.start)
    val dataArray = Array.ofDim[Byte](range.contentLength)
    val bytesRead = raf.read(dataArray, 0, range.contentLength)
    dataArray.take(bytesRead)
  }
}
