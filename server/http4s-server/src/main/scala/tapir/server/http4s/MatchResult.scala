package tapir.server.http4s

private[http4s] case class MatchResult[F[_]](values: List[Any], ctx: Context[F]) {
  def prependValue(v: Any): MatchResult[F] = copy(values = v :: values)
}
