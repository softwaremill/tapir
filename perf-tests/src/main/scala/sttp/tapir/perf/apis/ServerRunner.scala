package sttp.tapir.perf.apis

import cats.effect.IO

trait ServerRunner {
  def start: IO[ServerRunner.KillSwitch]
}

object ServerRunner {
  type KillSwitch = IO[Unit]
}
