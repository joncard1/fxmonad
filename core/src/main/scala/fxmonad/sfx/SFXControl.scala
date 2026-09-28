package fxmonad.sfx

import fxmonad.ControlBase
import fxmonad.Conversion

/** A parent class for controls based on ScalaFX controls.
  *
  * @param control
  *   The ScalaFX control wrapped by this object.
  * @param inConversion
  *   A utility to convert value of the type exposed by this monad to the naive
  *   type of the control.
  * @param outConversion
  *   A utility to convert a value from the naive type of the control to the
  *   type exposed by this monad.jk
  */
abstract class SFXControl[
    COut,
    CIn,
    InnerControl <: scalafx.scene.control.Control
](val control: InnerControl)(using
    inConversion: Conversion[COut, CIn],
    outConversion: Conversion[CIn, COut]
) extends ControlBase(using inConversion, outConversion)
