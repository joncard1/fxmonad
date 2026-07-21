package jackflashtech.fxmonad.sfx

import scalafx.scene.control.ColorPicker
import jackflashtech.fxmonad.SFXControl
import scalafx.beans.property.Property
import scalafx.scene.paint.Color
import jackflashtech.fxmonad.ColorPickerProxy
import scalafx.application.Platform
import scalafx.scene.control.Tooltip
import scalafx.concurrent.Worker.State.Succeeded
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import scalafx.beans.property.ObjectProperty

object ColorPickerControl {
  given Conversion[Color, Color] = (x: Color) => x 
}

abstract class ColorPickerControl[COut](override val defaultProperty: Property[COut, ?], control: ColorPicker = new ColorPickerProxy())(using inConversion: Conversion[COut, Color], outConversion: Conversion[Color, COut]) extends SFXControl[COut, Color, ColorPicker](control)(using inConversion, outConversion) {
  import scalafx.Includes._

  control.value.onChange((_, _, newVal) => updateProperty(newVal))

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
    Try(inConversion(defaultProperty())) match {
      case Success(null) => showError("Property was set to null")
      case Success(nv) =>
        control.value() = nv
        clearError()
      case Failure(exception) => 
        showError(exception.getMessage())
    }
  })

  updateProperty(control.value())
}

class ColorPickerControlColor(control: ColorPicker = new ColorPickerProxy()) extends ColorPickerControl[Color](ObjectProperty[Color](Color.White), control)(using ColorPickerControl.given_Conversion_Color_Color, ColorPickerControl.given_Conversion_Color_Color) {
  def this(initialValue: Color) = {
    this()
    this.defaultProperty() = initialValue
  }

  def this(initialValue: Color, control: ColorPicker) = {
    this(control)
    this.defaultProperty() = initialValue
  }
}