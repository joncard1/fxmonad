package fxmonad.sfx

import scalafx.beans.property.BooleanProperty
import scalafx.beans.property.IntegerProperty
import scalafx.beans.property.StringProperty
import scalafx.scene.control.CheckBox
import scalafx.beans.property.Property
import scalafx.scene.control.Tooltip
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import fxmonad.Control
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
    control: CheckBox = CheckBoxProxy()
)(using
    inConversion: Conversion[COut, Boolean],
    outConversion: Conversion[Boolean, COut]
) extends SFXControl[COut, Boolean, CheckBox](control)(using
      inConversion,
      outConversion
    ) {
  override protected[fxmonad] def showError(errorMsg: String): Unit =
    control.tooltip() = Tooltip(errorMsg)
  override protected[fxmonad] def clearError(): Unit = control.tooltip() = null

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
