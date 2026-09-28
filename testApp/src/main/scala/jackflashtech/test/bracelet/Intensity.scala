package jackflashtech.test.bracelet

import eu.timepit.refined.numeric.Interval
import eu.timepit.refined.refineV

/** The predicate constraining [[Intensity]] to the closed range [0, 100]. */
type IntensityRange = Interval.Closed[0, 100]

/** An Int refined (via eu.timepit.refined) to the inclusive range 0 to 100,
  * used as the value type for every bracelet intensity display and control.
  *
  * Wrapped in a nominal case class - rather than used as a bare
  * `Int Refined IntensityRange` - because refined's Scala 3 `Refined` type is
  * an opaque type with no runtime class of its own, and fxmonad's
  * `FXMonad`/`Control` lookup registry keys its widget bindings by
  * `classOf[T]`, which needs a real class to key on.
  */
final case class Intensity private (value: Int)

object Intensity {
  val min: Intensity = unsafeFrom(0)
  val max: Intensity = unsafeFrom(100)

  def from(value: Int): Either[String, Intensity] =
    refineV[IntensityRange](value).map(_ => new Intensity(value))

  def unsafeFrom(value: Int): Intensity =
    from(value).fold(err => throw new IllegalArgumentException(err), identity)

  /** Fits an arbitrary Int into the valid range instead of failing. */
  def clamped(value: Int): Intensity = unsafeFrom(
    math.max(0, math.min(100, value))
  )
}
