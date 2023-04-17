package sttp.tapir

import sttp.model.MediaType
import sttp.model.headers.ETag
import sttp.tapir.internal.MimeByExtensionDB

package object files extends TapirStaticContentEndpoints {
  def defaultETag(lastModified: Long, range: Option[RangeValue], length: Long): ETag = {
    val rangeSuffix = range.flatMap(_.startAndEnd).map { case (start, end) => s"-${start.toHexString}-${end.toHexString}" }.getOrElse("")
    ETag(s"${lastModified.toHexString}-${length.toHexString}$rangeSuffix")
  }

  private[tapir] def isModified(staticInput: StaticInput, etag: Option[ETag], lastModified: Long): Boolean = {
    etag match {
      case None => isModifiedByModifiedSince(staticInput, lastModified)
      case Some(et) =>
        val ifNoneMatch = staticInput.ifNoneMatch.getOrElse(Nil)
        if (ifNoneMatch.nonEmpty) ifNoneMatch.forall(e => e.tag != et.tag)
        else true
    }
  }

  private[tapir] def isModifiedByModifiedSince(staticInput: StaticInput, lastModified: Long): Boolean = staticInput.ifModifiedSince match {
    case Some(i) => lastModified > i.toEpochMilli
    case None    => true
  }

  private[tapir] def contentTypeFromName(name: String): MediaType = {
    val ext = name.substring(name.lastIndexOf(".") + 1)
    MimeByExtensionDB(ext).getOrElse(MediaType.ApplicationOctetStream)
  }
}

