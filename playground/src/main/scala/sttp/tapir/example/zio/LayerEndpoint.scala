package sttp.tapir.examples

import zio.{Has, ZIO, ZLayer}
import zio.console.Console
import sttp.tapir.examples.ZioService.Pet

object LayerEndpoint {
  type UserService = Has[UserService.Service]

  object UserService {
    trait Service {
      def hello(id: Int): ZIO[Any, String, Pet]
    }

    val live: ZLayer[Console, Nothing, Has[Service]] = ZLayer.fromFunction { console: Console =>
      new Service {
        def hello(id: Int): ZIO[Any, String, Pet] = {
          console.get.putStrLn(s"Got Pet request for $id")
          ZIO.succeed(Pet(id.toString, "https://zio.dev"))
        }
      }
    }

    def hello(id: Int): ZIO[UserService, String, Pet] = ZIO.accessM(_.get.hello(id))
  }

  val liveEnv: ZLayer[Any, Nothing, Has[UserService.Service]] = Console.live >>> UserService.live
}
