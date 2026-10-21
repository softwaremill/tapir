package sttp.tapir.json.pickler.internal.compiletime

import scala.concurrent.duration.*
import scala.util.Try

/** Reads the derivation timeout from `-Xmacro-settings:<namespace>.timeout=...`, given in seconds or as a `Duration` string (`30s`,
  * `2 minutes`). An invalid value fails the compilation.
  */
trait DerivationTimeout { this: hearth.MacroCommons =>

  protected def derivationSettingsNamespace: String

  // Read raw: Hearth's typed settings turn a bare `120` into a Double.
  protected lazy val derivationTimeout: FiniteDuration =
    Environment.XMacroSettings.collect { case s"$key=$value" if key == s"$derivationSettingsNamespace.timeout" => value }.lastOption match {
      case None        => DerivationTimeout.Default
      case Some(value) =>
        DerivationTimeout
          .parse(value)
          .getOrElse(
            Environment.reportErrorAndAbort(
              s"$derivationSettingsNamespace.timeout: expected a positive number of seconds or a duration such as 30s, got '$value'"
            )
          )
    }
}

object DerivationTimeout {

  /** Hearth's own default (2s) fails large but legitimate derivations on a busy machine; runaway recursion is caught separately. */
  val Default: FiniteDuration = 60.seconds

  def parse(value: String): Option[FiniteDuration] =
    value.trim.toLongOption
      .map(_.seconds)
      .orElse(Try(Duration(value)).toOption.collect { case d: FiniteDuration => d })
      .filter(_ > Duration.Zero)
}
