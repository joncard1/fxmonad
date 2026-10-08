package fxmonad.sfx

import scalafx.beans.property.Property
import scalafx.scene.control.Slider
import fxmonad.Control
import fxmonad.sfx.SFXControl
import fxmonad.PropertyConstructor
import fxmonad.Conversion

object SliderControl {
  def apply[A: PropertyConstructor]()(using
      inConversion: Conversion[A, Double],
      outConversion: Conversion[Double, A]
  ): SliderControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    new SliderControl(constructor())
  }
  def apply[A: PropertyConstructor](initialValue: A)(using
      inConversion: Conversion[A, Double],
      outConversion: Conversion[Double, A]
  ): SliderControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    val newC = new SliderControl(constructor())
    newC() = initialValue
    newC
  }

  def apply[A: PropertyConstructor](control: Slider)(using
      inConversion: Conversion[A, Double],
      outConversion: Conversion[Double, A]
  ): SliderControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    new SliderControl(constructor(), control)
  }

  def apply[A: PropertyConstructor](initialValue: A, control: Slider)(using
      inConversion: Conversion[A, Double],
      outConversion: Conversion[Double, A]
  ): SliderControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    val newC = new SliderControl(constructor(), control)
    newC() = initialValue
    newC
  }
}

class SliderControl[COut](
    override val defaultProperty: Property[COut, ?],
    override val control: Slider = new SliderProxy()
)(using
    inConversion: Conversion[COut, Double],
    outConversion: Conversion[Double, COut]
) extends SFXControl[COut, Double, Slider](control)(using
      inConversion,
      outConversion
    )
    with TooltipValidationErrorStrategy[COut, Double, Slider] {

  override protected[fxmonad] def updateFrom
      : PartialFunction[Control[COut], Unit] = {
    val updateFromProxy: PartialFunction[Control[COut], Unit] = {
      case source: SliderControl[?]
          if source.control.isInstanceOf[SliderProxy] =>
        source.control.asInstanceOf[SliderProxy].applyChanges(control)
    }
    updateFromProxy.orElse(super.updateFrom)
  }

  private val _ = control.value.onChange((_, _, newVal) =>
    updateProperty(newVal.doubleValue())
  )

  private val _ = defaultProperty.onChange((_, _, _) => {
    inConversion(defaultProperty()) match {
      case Right(nv) =>
        control.value() = nv
        clearError()
      case Left(msg) =>
        showError(msg)

    }
  })

  updateProperty(control.value())
}
