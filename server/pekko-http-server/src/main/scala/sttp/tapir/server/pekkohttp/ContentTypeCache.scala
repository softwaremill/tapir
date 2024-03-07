package sttp.tapir.server.pekkohttp

import org.apache.pekko.http.scaladsl.model.ContentType
import scala.collection.concurrent.TrieMap

/** Pekko-specific ConentType has to be created if an endpoint overrides it, but we want to reduce overhead of the expensive
  * ContentType.parse operation if possible. Parsing may also happen for cases not listed explictly in
  * PekkoToResponseBody.formatToContentType. This cache doesn't have to save atomically, because the worst case scenario is that we parse he
  * same header a few times before it's saved. The cache is not cleared, because the number of different content types is limited and the
  * cache is not expected to grow too much. The only exception is when there is a boundary in the header, but in such situation the endpoint
  * contentType shouldn't be overriden. Just in case this happens, we limit the cache size.
  */
private[pekkohttp] object ContentTypeCache {
  private val cache = TrieMap[String, ContentType]()
  private val Limit = 100

  def getOrParse(headerValue: String): ContentType = {
    cache.get(headerValue) match {
      case Some(contentType) =>
        contentType
      case None =>
        val contentType =
          ContentType.parse(headerValue).getOrElse(throw new IllegalArgumentException(s"Cannot parse content type: $headerValue"))
        // We don't want to fill the cache with parameterized media types (BTW charset does not appear in params)
        val _ = if (cache.size <= Limit && contentType.mediaType.params.isEmpty) cache.putIfAbsent(headerValue, contentType)
        contentType
    }
  }
}
