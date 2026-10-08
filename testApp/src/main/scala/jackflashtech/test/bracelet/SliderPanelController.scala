package jackflashtech.test.bracelet

import fxmonad._
import scala.annotation.experimental

/** Controller for the on-screen "bracelet" replacement: six sliders, each
  * exposed as a Control[Intensity]. Only slider1 is wired up to anything by
  * [[BraceletController]] today; sliders 2-6 exist for future use.
  */
@experimental
class SliderPanelController {

  @FXMonad("intensityControl1")
  lazy val intensity1: Control[Intensity] = ???

  @FXMonad("intensityControl2")
  lazy val intensity2: Control[Intensity] = ???

  @FXMonad("intensityControl3")
  lazy val intensity3: Control[Intensity] = ???

  @FXMonad("intensityControl4")
  lazy val intensity4: Control[Intensity] = ???

  @FXMonad("intensityControl5")
  lazy val intensity5: Control[Intensity] = ???

  @FXMonad("intensityControl6")
  lazy val intensity6: Control[Intensity] = ???

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
