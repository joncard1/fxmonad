package jackflashtech.test.bracelet

import scala.compiletime.error

opaque type Intensity2 <: Int = Int

object Intensity2 {
  inline def apply(inline v: Int): Intensity2 = inline if ((v < 0) || ( v > 100)) error("Intensity values must be between 0 and 100, inclusive.") else v

  extension (inline n: Int)
    inline def asIntensity: Intensity2 = Intensity2(n)
}
