package sttp.tapir.perf.apis

import sttp.tapir.perf.Common._

sealed trait ServerName {
  def shortName: String
  def fullName: String
}
case class KnownServerName(shortName: String, fullName: String) extends ServerName

/** Used when running a suite without specifying servers and assuming that a server has been started externally. */
case object ExternalServerName extends ServerName {
  override def shortName: String = "External"
  override def fullName: String = "External"
}

object ServerName {
  def fromShort(shortName: String): ServerName =
    KnownServerName(shortName, s"${rootPackage}.${shortName}Server")
}
