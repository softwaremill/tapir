package sttp.tapir.server

import java.io.InputStream

package object jdkhttp {

  type HttpServer = com.sun.net.httpserver.HttpServer
  type HttpsConfigurator = com.sun.net.httpserver.HttpsConfigurator

  type JdkHttpResponseBody = (InputStream, Option[Long])
}
