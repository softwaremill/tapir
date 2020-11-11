package sttp.tapir.bimap

case class BiMap[X, Y](map: Map[X, Y], dX: Option[X], dY: Option[Y]) {
  def this(tuples: (X, Y)*) = this(tuples.toMap, None, None)

  def this(map: Map[X, Y]) = this(map, None, None)

  private val reverseMap = map map (_.swap)
  require(map.size == reverseMap.size, "The given data has no 1 to 1 relation!")
  require(dX.isEmpty == dY.isEmpty, "Both default values should be either set or unset!")

  /**
   * @return The element assigned to x, or with the default if has any.
   * @throws java.util.NoSuchElementException if no element assigned to x and no default value set.
   */
  def apply(x: X): Y = extractY(x)

  /**
   * @return The element assigned to y, or with the default if has any.
   * @throws java.util.NoSuchElementException if no element assigned to x and no default value set.
   */
  def apply(y: Y)(implicit d: DummyImplicit): X = extractX(y)

  /**
   * @return The element assigned to x, or with the default if has any.
   * @throws java.util.NoSuchElementException if no element assigned to x and no default value set.
   */
  def extractY(x: X): Y = map.get(x).orElse(dY).get

  /**
   * @return The element assigned to y, or with the default if has any.
   * @throws java.util.NoSuchElementException if no element assigned to x and no default value set.
   */
  def extractX(y: Y): X = reverseMap.get(y).orElse(dX).get

  val domain = map.keys
  val codomain = reverseMap.keys
}

object syntax {

  implicit class MapToBimap[K, V](val m: Map[K, V]) extends AnyVal {
    /**
     * Creates a [[sttp.tapir.bimap.BiMap]] from the given Map.
     *
     * @throws java.lang.IllegalArgumentException if the given map has no valid 1 to 1 relation!
     */
    def toBiMap: BiMap[K, V] = {
      new BiMap(m)
    }

    /**
     * Creates a [[sttp.tapir.bimap.BiMap]] from the given Map with default values.
     *
     * @throws java.lang.IllegalArgumentException if the given map has no valid 1 to 1 relation!
     */
    def toBimapWithDefaults(defaultV: V, defaultK: K): BiMap[K, V] = {
      BiMap(m, Option(defaultK), Option(defaultV))
    }
  }

}
