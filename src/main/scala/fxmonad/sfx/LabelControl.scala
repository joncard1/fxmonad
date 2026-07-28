package fxmonad.sfx

import scalafx.scene.control.Label
import fxmonad.sfx.SFXControl
import scalafx.beans.property.Property
import fxmonad.PropertyConstructor
import fxmonad.Conversion

object LabelControl {
  def apply[A: PropertyConstructor]()(using
      inConversion: Conversion[A, String],
      outConversion: Conversion[String, A]
  ): LabelControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    new LabelControl(constructor())
  }
  def apply[A: PropertyConstructor](initialValue: A)(using
      pc: PropertyConstructor[A],
      inConversion: Conversion[A, String],
      outConversion: Conversion[String, A]
  ): LabelControl[A] = {
    val newC = new LabelControl(pc())(using inConversion, outConversion)
    newC() = initialValue
    newC
  }

  def apply[A: PropertyConstructor](control: Label)(using
      inConversion: Conversion[A, String],
      outConversion: Conversion[String, A]
  ): LabelControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    new LabelControl(constructor(), control)
  }

  def apply[A: PropertyConstructor](initialValue: A, control: Label)(using
      pc: PropertyConstructor[A],
      inConversion: Conversion[A, String],
      outConversion: Conversion[String, A]
  ): LabelControl[A] = {
    val newC = new LabelControl(pc(), control)
    newC() = initialValue
    newC
  }
}

class LabelControl[COut](
    defaultProperty: Property[COut, ?],
    control: Label = new LabelProxy()
)(using
    inConversion: Conversion[COut, String],
    outConversion: Conversion[String, COut]
) extends SFXControl[COut, String, Label](defaultProperty, control)(using
      inConversion,
      outConversion
    ) {
  // Not bothering to subscribe to property changes because it's a read-only control

  override protected[fxmonad] def clearError(): Unit = {}
  override protected[fxmonad] def showError(errorMsg: String): Unit = {}

  defaultProperty.onChange((_, _, newVal) => {
    inConversion(defaultProperty()) match {
      case Right(null) =>
        showError("This control was given a value that converted to null")
      case Right(nv) =>
        control.text() = nv
        clearError()
      case Left(msg) => showError(msg)
    }
  })
}
