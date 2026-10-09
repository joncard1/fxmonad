package fxmonad.sfx

import scalafx.scene.control.CheckBox
import fxmonad.Control
import scalafx.beans.property.Property
import fxmonad.sfx.SFXControl
import fxmonad.PropertyConstructor
import fxmonad.Conversion

object CheckBoxControl {
  def apply[A: PropertyConstructor]()(using
      inConversion: Conversion[A, Boolean],
      outConversion: Conversion[Boolean, A]
  ): CheckBoxControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    new CheckBoxControl(constructor())
  }
  def apply[A: PropertyConstructor](initialValue: A)(using
      inConversion: Conversion[A, Boolean],
      outConversion: Conversion[Boolean, A]
  ) = {
    val constructor = summon[PropertyConstructor[A]]
    val newC = new CheckBoxControl(constructor())
    newC() = initialValue
    newC
  }

  def apply[A: PropertyConstructor](control: CheckBox)(using
      inConversion: Conversion[A, Boolean],
      outConversion: Conversion[Boolean, A]
  ): CheckBoxControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    new CheckBoxControl(constructor(), control)
  }

  def apply[A: PropertyConstructor](initialValue: A, control: CheckBox)(using
      inConversion: Conversion[A, Boolean],
      outConversion: Conversion[Boolean, A]
  ) = {
    val constructor = summon[PropertyConstructor[A]]
    val newC = new CheckBoxControl(constructor(), control)
    newC() = initialValue
    newC
  }
}

class CheckBoxControl[COut](
    override val defaultProperty: Property[COut, ?],
    override val control: CheckBox = CheckBoxProxy()
)(using
    inConversion: Conversion[COut, Boolean],
    outConversion: Conversion[Boolean, COut]
) extends SFXControl[COut, Boolean, CheckBox](control)(using
      outConversion
    )
    with TooltipValidationErrorStrategy[COut, Boolean, CheckBox] {

  override protected[fxmonad] def updateFrom
      : PartialFunction[Control[COut], Unit] = {
    val updateFromProxy: PartialFunction[Control[COut], Unit] = {
      case source: CheckBoxControl[?]
          if source.control.isInstanceOf[CheckBoxProxy] =>
        source.control.asInstanceOf[CheckBoxProxy].applyChanges(control)
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
