package fxmonad.sfx

import scalafx.scene.control.Label
import fxmonad.Control
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
      inConversion: Conversion[A, String],
      outConversion: Conversion[String, A]
  ): LabelControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    val newC =
      new LabelControl(constructor())(using inConversion, outConversion)
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
      inConversion: Conversion[A, String],
      outConversion: Conversion[String, A]
  ): LabelControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    val newC = new LabelControl(constructor(), control)
    newC() = initialValue
    newC
  }
}

class LabelControl[COut](
    override val defaultProperty: Property[COut, ?],
    override val control: Label = new LabelProxy()
)(using
    inConversion: Conversion[COut, String],
    outConversion: Conversion[String, COut]
) extends SFXControl[COut, String, Label](control)(using
      inConversion,
      outConversion
    ) {
  // Not bothering to subscribe to property changes because it's a read-only control

  override protected[fxmonad] def updateFrom: PartialFunction[Control[COut], Unit] = {
    val updateFromProxy: PartialFunction[Control[COut], Unit] = {
      case source: LabelControl[?]
          if source.control.isInstanceOf[LabelProxy] =>
        source.control.asInstanceOf[LabelProxy].applyChanges(control)
    }
    updateFromProxy.orElse(super.updateFrom)
  }

  override def clearError(): Unit = {}
  override def showError(errorMsg: String): Unit = {}

  private val _ = defaultProperty.onChange((_, _, _) => {
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
