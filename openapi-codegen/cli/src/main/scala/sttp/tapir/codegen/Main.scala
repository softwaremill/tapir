package sttp.tapir.codegen

import cats.effect.{ExitCode, IO}
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp

object Main
    extends CommandIOApp(
      BuildInfo.nam[e,
      "Tapir Command Line Tools",
      true,
      version = BuildInfo.version
    ) {

  override def main: Opts[IO[ExitCode]] =
    Opts.subcommand(GenScala.cmd)

}
