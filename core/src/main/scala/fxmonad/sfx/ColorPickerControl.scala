package fxmonad.sfx

import scalafx.scene.control.ColorPicker
import fxmonad.Control
import fxmonad.sfx.SFXControl
import scalafx.beans.property.Property
import scalafx.scene.paint.Color
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import scalafx.beans.property.ObjectProperty

object ColorPickerControl {
  given Conversion[Color, Color] = (x: Color) => x
}

class ColorPickerControl[COut](
    override val defaultProperty: Property[COut, ?],
    override val control: ColorPicker = new ColorPickerProxy()
)(using
    inConversion: Conversion[COut, Color],
    outConversion: Conversion[Color, COut]
) extends SFXControl[COut, Color, ColorPicker](control)(using
      outConversion
    )
    with TooltipValidationErrorStrategy[COut, Color, ColorPicker] {
  import scalafx.Includes._

  override protected[fxmonad] def updateFrom
      : PartialFunction[Control[COut], Unit] = {
    val updateFromProxy: PartialFunction[Control[COut], Unit] = {
      case source: ColorPickerControl[?]
          if source.control.isInstanceOf[ColorPickerProxy] =>
        source.control.asInstanceOf[ColorPickerProxy].applyChanges(control)
    }
    updateFromProxy.orElse(super.updateFrom)
  }

  private val _ =
    control.value.onChange((_, _, newVal) => updateProperty(newVal))

  private val _ = defaultProperty.onChange((_, _, _) => {
    Try(inConversion(defaultProperty())) match {
      case Success(null) => showError("Property was set to null")
      case Success(nv)   =>
        control.value() = nv
        clearError()
      case Failure(exception) =>
        showError(exception.getMessage())
    }
  })

  updateProperty(control.value())
}

// TODO: Why does this exist? I think this was an older paradigm for implementing these and can be deleted.
//  This requires putting 4 different ColorPickerControl.apply methods, and I don't have time right now.
class ColorPickerControlColor(
    override val control: ColorPicker = new ColorPickerProxy()
) extends ColorPickerControl[Color](
      ObjectProperty[Color](Color.White),
      control
    )(using
      ColorPickerControl.given_Conversion_Color_Color,
      ColorPickerControl.given_Conversion_Color_Color
    ) {
  def this(initialValue: Color) = {
    this()
    this.defaultProperty() = initialValue
  }

  def this(initialValue: Color, control: ColorPicker) = {
    this(control)
    this.defaultProperty() = initialValue
  }
}
