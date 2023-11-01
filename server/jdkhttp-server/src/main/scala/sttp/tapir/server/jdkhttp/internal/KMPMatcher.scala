package sttp.tapir.server.jdkhttp.internal

import scala.collection.mutable

class KMPMatcher(delimiter: Array[Byte]) {
  private val table = KMPMatcher.buildLongestPrefixSuffixTable(delimiter)
  private var matches: Int = 0

  def getMatches: Int = this.matches
  def getDelimiter: Array[Byte] = this.delimiter

  def matchByte(b: Byte): Boolean = {
    while (getMatches > 0 && b != delimiter(getMatches)) {
      this.matches = this.table(getMatches - 1)
    }

    if (b == delimiter(matches)) {
      matches += 1
      if (this.matches == delimiter.length) {
        this.matches = 0
        true
      } else false
    } else false
  }
}

object KMPMatcher {
  private def buildLongestPrefixSuffixTable(s: Array[Byte]): mutable.ArrayBuffer[Int] = {
    val lookupTable = mutable.ArrayBuffer.fill(s.length)(-1)
    lookupTable(0) = 0
    var len = 0
    var i = 1
    while (i < s.length) {
      if (s(i) == s(len)) {
        len += 1
        lookupTable(i) = len
        i += 1
      } else {
        if (len == 0) {
          lookupTable(i) = 0
          i = i + 1
        } else {
          len = lookupTable(len - 1)
        }
      }
    }
    lookupTable
  }
}
