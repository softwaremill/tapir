package sttp.tapir.server.finatra

import com.twitter.finagle.http.{Method, Request, Response}
import com.twitter.util.Future

case class FinatraRoute(handler: Request => Future[Response], method: Method, path: String)
