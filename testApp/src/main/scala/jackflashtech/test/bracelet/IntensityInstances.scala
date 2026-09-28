package jackflashtech.test.bracelet

import fxmonad.{Conversion, PropertyConstructor}
import scalafx.beans.property.ObjectProperty

/** Bridges [[Intensity]] into the fxmonad control system: how to construct a
  * backing property for it, and how to convert it to/from the underlying types
  * (Double for sliders, String for text displays).
  */
object IntensityInstances {
  given PropertyConstructor[Intensity] = () =>
    ObjectProperty[Intensity](Intensity.min)

  given Conversion[Intensity, Double] = (x: Intensity) =>
    Right(x.value.toDouble)
  given Conversion[Double, Intensity] = (x: Double) =>
    Right(Intensity.clamped(math.round(x).toInt))

  given Conversion[Intensity, String] = (x: Intensity) =>
    Right(x.value.toString)
  given Conversion[String, Intensity] = (x: String) =>
    x.toIntOption.toRight(s"'$x' is not a whole number").flatMap(Intensity.from)

  given Conversion[Intensity, Int] = (x: Intensity) => Right(x.value)
  given Conversion[Int, Intensity] = (x: Int) => Intensity.from(x)
}
