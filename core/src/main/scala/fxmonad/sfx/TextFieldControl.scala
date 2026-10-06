package fxmonad.sfx

import scalafx.beans.property.Property
import scalafx.scene.control.TextField
import fxmonad.Control
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
    override val defaultProperty: Property[COut, ?],
    override val control: TextField = new TextFieldProxy()
)(using
    inConversion: Conversion[COut, String],
    outConversion: Conversion[String, COut]
) extends SFXControl[COut, String, TextField](control)(using
      inConversion,
      outConversion
    )
    with TooltipValidationErrorStrategy[COut, String, TextField] {

  override protected[fxmonad] def updateFrom: PartialFunction[Control[COut], Unit] = {
    val updateFromProxy: PartialFunction[Control[COut], Unit] = {
      case source: TextFieldControl[?]
          if source.control.isInstanceOf[TextFieldProxy] =>
        source.control.asInstanceOf[TextFieldProxy].applyChanges(control)
    }
    updateFromProxy.orElse(super.updateFrom)
  }

  private val _ =
    control.text.onChange((_, _, newVal) => updateProperty(newVal))

  private val _ = defaultProperty.onChange((_, _, _) => {
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
