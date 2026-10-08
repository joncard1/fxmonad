package jackflashtech.test.bracelet

import fxmonad.{Conversion}
import jackflashtech.test.bracelet.Intensities._

/** Bridges [[Intensity]] into the fxmonad control system: how to convert it
  * to/from the underlying types (Double for sliders, String for text displays).
  */
object IntensityInstances {
  given Conversion[Intensity, Double] = (x: Intensity) =>
    Right(x.asInt.toDouble)
  given Conversion[Double, Intensity] = (x: Double) =>
    x.asIntensity

  given Conversion[Intensity, String] = (x: Intensity) =>
    Right(x.asInt.toString)
  given Conversion[String, Intensity] = (x: String) =>
    x.toIntOption.toRight(s"'$x' is not a whole number").flatMap(_.asIntensity)

  given Conversion[Intensity, Int] = (x: Intensity) => Right(x.asInt)
  given Conversion[Int, Intensity] = (x: Int) => x.asIntensity
}
