package sttp.tapir.cli

import cats.effect.{ExitCode, IO}
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp

object Main
    extends CommandIOApp(
      BuildInfo.name,
      "Tapir Command Line Tools",
      true,
      version = BuildInfo.version
    ) {

  override def main: Opts[IO[ExitCode]] =
    Opts.subcommand(GenScala.cmd)

}
