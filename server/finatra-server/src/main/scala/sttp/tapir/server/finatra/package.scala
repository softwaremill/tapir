package sttp.tapir.server

import com.twitter.util.Future

package object finatra {
  type FinatraServerRoutes = ServerRoutes[Future, FinatraRoute]
}
