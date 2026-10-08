package fxmonad.sfx

import scalafx.scene.control.RadioButton
import fxmonad.Control
import scalafx.beans.property.Property
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
    override val control: RadioButton = RadioButtonProxy()
)(using
    inConversion: Conversion[COut, Boolean],
    outConversion: Conversion[Boolean, COut]
) extends SFXControl[COut, Boolean, RadioButton](control)(using
      inConversion,
      outConversion
    )
    with TooltipValidationErrorStrategy[COut, Boolean, RadioButton] {

  override protected[fxmonad] def updateFrom
      : PartialFunction[Control[COut], Unit] = {
    val updateFromProxy: PartialFunction[Control[COut], Unit] = {
      case source: RadioButtonControl[?]
          if source.control.isInstanceOf[RadioButtonProxy] =>
        source.control.asInstanceOf[RadioButtonProxy].applyChanges(control)
    }
    updateFromProxy.orElse(super.updateFrom)
  }

  private val _ =
    control.selected.onChange((_, _, newVal) => updateProperty(newVal))

  private val _ = defaultProperty.onChange((_, _, _) => {
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
