package sttp.tapir.server.opentelemetry

import sttp.tapir.Endpoint

sealed trait SpanNaming
object SpanNaming {
  /** Utilise le format par défaut : "METHOD /path" */
  case object Default extends SpanNaming
  
  /** Utilise uniquement le chemin de l'endpoint */
  case object Path extends SpanNaming
  
  /** Permet une personnalisation complète du nommage des spans */
  case class Custom(f: Endpoint[_, _, _, _, _] => String) extends SpanNaming
}