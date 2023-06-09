package sttp.tapir.server

import java.io.InputStream

package object jdkhttp {

  type JdkHttpResponseBody = (InputStream, Option[Long])

  type Id[A] = A
}
