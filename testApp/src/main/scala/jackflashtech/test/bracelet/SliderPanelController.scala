package jackflashtech.test.bracelet

import fxmonad._
import scala.annotation.experimental

/** Controller for the on-screen "bracelet" replacement: six sliders, each
  * exposed as a Control[Intensity]. Only slider1 is wired up to anything by
  * [[BraceletController]] today; sliders 2-6 exist for future use.
  */
@experimental
class SliderPanelController {

  @FXMonad("sliderControl1")
  lazy val slider1: Control[Intensity] = ???

  @FXMonad("sliderControl2")
  lazy val slider2: Control[Intensity] = ???

  @FXMonad("sliderControl3")
  lazy val slider3: Control[Intensity] = ???

  @FXMonad("sliderControl4")
  lazy val slider4: Control[Intensity] = ???

  @FXMonad("sliderControl5")
  lazy val slider5: Control[Intensity] = ???

  @FXMonad("sliderControl6")
  lazy val slider6: Control[Intensity] = ???
}
