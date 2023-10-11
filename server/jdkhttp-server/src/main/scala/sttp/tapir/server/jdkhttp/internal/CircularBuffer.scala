package sttp.tapir.server.jdkhttp.internal

import scala.collection.IndexedSeqView

class CircularBuffer(bufferSize: Int) {
  private var currentIndex = -1
  private val underlying = new Array[Byte](bufferSize)
  private var readBytes = 0

  def length: Int = readBytes

  def reset(): Unit = {
    readBytes = 0
    currentIndex = -1
  }

  def addByte(byte: Byte): Unit = {
    if (currentIndex < bufferSize - 1) currentIndex += 1
    else currentIndex = 0

    underlying.update(currentIndex, byte)
    readBytes += 1
  }

  def isFull: Boolean = readBytes >= bufferSize

  def getString: String = new String(getBytes.toArray)

  private def lastNBytes(n: Int) = {
    val byteView = getBytes
    byteView.slice(byteView.length - n, byteView.length)
  }

  def endsWith(bytes: Array[Byte]): Boolean = lastNBytes(bytes.length).sameElements(bytes)

  def getBytes: IndexedSeqView[Byte] = {
    val idx = readBytes % bufferSize

    val b1 =
      if (readBytes >= bufferSize) underlying.view.slice(idx, bufferSize)
      else underlying.view.slice(0, 0)

    val b2: IndexedSeqView[Byte] = underlying.view.slice(0, idx)
    b1.concat(b2)
  }
}
