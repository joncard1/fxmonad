package jackflashtech.test.bracelet

import scala.compiletime.error
import jackflashtech.test.bracelet.Intensities._

case class Intensity private (v: IntensityRange) {
  def asInt: Int = v.asInt
  def +(v2: Intensity): Intensity =
    (v.asInt + v2.v.asInt).asIntensity.fold(_ => Intensity.max, i => i)
  def -(v2: Intensity): Intensity =
    (v.asInt - v2.v.asInt).asIntensity.fold(_ => Intensity.min, i => i)
}

object Intensity {
  val min: Intensity = Intensity(IntensityRange(0))
  val max: Intensity = Intensity(IntensityRange(100))

  def apply(v: IntensityRange) = new Intensity(v)
}

object Intensities {
  opaque type IntensityRange = Int

  object IntensityRange {
    private inline val errMsg =
      "Intensity values must be between 0 and 100, inclusive."

    inline def apply(inline v: Int): IntensityRange =
      inline if ((v < 0) || (v > 100)) error(errMsg) else v

    def from(v: Int): Either[String, IntensityRange] =
      if ((v < 0) || (v > 100)) Left(errMsg) else Right(v)
  }

  extension (n: Int)
    def asIntensity: Either[String, Intensity] =
      IntensityRange.from(n).map(n => Intensity(n))

  extension (n: Double)
    def asIntensity: Either[String, Intensity] =
      IntensityRange.from(math.round(n).toInt).map(n => Intensity(n))

  extension (n: IntensityRange) def asInt: Int = n
}
