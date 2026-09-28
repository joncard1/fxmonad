package fxmonad.sfx

import scalafx.scene.control.RadioButton
import scalafx.beans.property.Property
import scalafx.scene.control.Tooltip
import fxmonad.sfx.SFXControl
import fxmonad.PropertyConstructor
import fxmonad.Conversion

object RadioButtonControl {
  def apply[A: PropertyConstructor]()(using
      inConversion: Conversion[A, Boolean],
      outConversion: Conversion[Boolean, A]
  ): RadioButtonControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    new RadioButtonControl(constructor())
  }
  def apply[A: PropertyConstructor](initialValue: A)(using
      inConversion: Conversion[A, Boolean],
      outConversion: Conversion[Boolean, A]
  ) = {
    val constructor = summon[PropertyConstructor[A]]
    val newC = new RadioButtonControl(constructor())
    newC() = initialValue
    newC
  }

  def apply[A: PropertyConstructor](control: RadioButton)(using
      inConversion: Conversion[A, Boolean],
      outConversion: Conversion[Boolean, A]
  ): RadioButtonControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    new RadioButtonControl(constructor(), control)
  }

  def apply[A: PropertyConstructor](initialValue: A, control: RadioButton)(using
      inConversion: Conversion[A, Boolean],
      outConversion: Conversion[Boolean, A]
  ) = {
    val constructor = summon[PropertyConstructor[A]]
    val newC = new RadioButtonControl(constructor(), control)
    newC() = initialValue
    newC
  }
}

class RadioButtonControl[COut](
    override val defaultProperty: Property[COut, ?],
    control: RadioButton = RadioButtonProxy()
)(using
    inConversion: Conversion[COut, Boolean],
    outConversion: Conversion[Boolean, COut]
) extends SFXControl[COut, Boolean, RadioButton](control)(using
      inConversion,
      outConversion
    ) {
  override protected def displayError(errorMsg: String): Unit =
    control.tooltip() = Tooltip(errorMsg)
  override protected def clearDisplayedError(): Unit = control.tooltip() = null

  control.selected.onChange((_, _, newVal) => updateProperty(newVal))

  defaultProperty.onChange((_, _, newVal) => {
    inConversion(defaultProperty()) match {
      case Right(nv) =>
        control.selected() = nv
        clearError()
      case Left(msg) =>
        showError(msg)
    }
  })

  updateProperty(control.selected())
}
