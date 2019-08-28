package tapir.model

class MultiQueryParams(ps: Map[String, Seq[String]]) {
  def toMap: Map[String, String] = toSeq.toMap
  def toMultiMap: Map[String, Seq[String]] = ps
  def toSeq: Seq[(String, String)] = ps.toSeq.flatMap { case (k, vs) => vs.map((k, _)) }

  def get(s: String): Option[String] = ps.get(s).flatMap(_.headOption)
  def getMulti(s: String): Option[Seq[String]] = ps.get(s)
}

object MultiQueryParams {
  def fromMap(m: Map[String, String]): MultiQueryParams = new MultiQueryParams(m.mapValues(List(_)).toMap)
  def fromSeq(s: Seq[(String, String)]): MultiQueryParams =
    // use groupMap in scala 2.13 instead of groupBy, mapValues
    new MultiQueryParams(s.groupBy(_._1).mapValues(_.map(_._2)).toMap)
  def fromMultiMap(m: Map[String, Seq[String]]): MultiQueryParams = new MultiQueryParams(m)
}
