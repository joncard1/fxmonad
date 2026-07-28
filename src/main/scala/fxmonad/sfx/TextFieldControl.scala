package fxmonad.sfx

import scalafx.beans.property.Property
import scalafx.scene.control.TextField
import scalafx.scene.control.Tooltip
import scalafx.application.Platform
import fxmonad.sfx.SFXControl
import fxmonad.Conversion
import fxmonad.PropertyConstructor

object TextFieldControl {
  def apply[A: PropertyConstructor]()(using
      inConversion: Conversion[A, String],
      outConversion: Conversion[String, A]
  ): TextFieldControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    new TextFieldControl(constructor())
  }
  def apply[A: PropertyConstructor](initialValue: A)(using
      inConversion: Conversion[A, String],
      outConversion: Conversion[String, A]
  ): TextFieldControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    val newC = new TextFieldControl(constructor())
    newC() = initialValue
    newC
  }

  def apply[A: PropertyConstructor](control: TextField)(using
      inConversion: Conversion[A, String],
      outConversion: Conversion[String, A]
  ): TextFieldControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    new TextFieldControl(constructor(), control)
  }

  def apply[A: PropertyConstructor](initialValue: A, control: TextField)(using
      inConversion: Conversion[A, String],
      outConversion: Conversion[String, A]
  ): TextFieldControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    val newC = new TextFieldControl(constructor(), control)
    newC() = initialValue
    newC
  }
}

class TextFieldControl[COut](
    defaultProperty: Property[COut, ?],
    control: TextField = new TextFieldProxy()
)(using
    inConversion: Conversion[COut, String],
    outConversion: Conversion[String, COut]
) extends SFXControl[COut, String, TextField](defaultProperty, control)(using
      inConversion,
      outConversion
    ) {

  control.text.onChange((_, _, newVal) => updateProperty(newVal))

  override protected[fxmonad] def clearError(): Unit = {
    control.tooltip() = null
    Platform.runLater {
      control.styleClass.removeAll("error")
    }
  }

  override protected[fxmonad] def showError(errorMsg: String): Unit = {
    control.tooltip() = Tooltip(errorMsg)
    Platform.runLater {
      control.styleClass.add("error")
    }
  }

  defaultProperty.onChange((_, _, newVal) => {
    inConversion(defaultProperty()) match {
      case Right(null) =>
        showError("Property was set to null")
      case Right(nv) =>
        control.text() = nv
        clearError()
      case Left(msg) =>
        showError(msg)
    }
  })

  updateProperty(control.text())
}
