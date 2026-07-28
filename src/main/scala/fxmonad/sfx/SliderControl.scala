package fxmonad.sfx

import scalafx.beans.property.Property
import scalafx.scene.control.Slider
import fxmonad.sfx.SFXControl
import scalafx.concurrent.Worker.State.Succeeded
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import scalafx.beans.property.IntegerProperty
import scalafx.application.Platform
import scalafx.scene.control.Tooltip
import scalafx.beans.property.DoubleProperty
import fxmonad.PropertyConstructor
import fxmonad.Conversion

object SliderControl {
    def apply[A: PropertyConstructor]()(using inConversion: Conversion[A, Double], outConversion: Conversion[Double, A]): SliderControl[A] = {
        val constructor = summon[PropertyConstructor[A]]
        new SliderControl(constructor())
    }
    def apply[A: PropertyConstructor](initialValue: A)(using inConversion: Conversion[A, Double], outConversion: Conversion[Double, A]): SliderControl[A] = {
        val constructor = summon[PropertyConstructor[A]]
        val newC = new SliderControl(constructor())
        newC() = initialValue
        newC
    }

    def apply[A: PropertyConstructor](control: Slider)(using inConversion: Conversion[A, Double], outConversion: Conversion[Double, A]): SliderControl[A] = {
        val constructor = summon[PropertyConstructor[A]]
        new SliderControl(constructor(), control)
    }

    def apply[A: PropertyConstructor](initialValue: A, control: Slider)(using inConversion: Conversion[A, Double], outConversion: Conversion[Double, A]): SliderControl[A] = {
        val constructor = summon[PropertyConstructor[A]]
        val newC = new SliderControl(constructor(), control)
        newC() = initialValue
        newC
    }
}

class SliderControl[COut](override val defaultProperty: Property[COut, ?], control: Slider = new SliderProxy())(using inConversion: Conversion[COut, Double], outConversion: Conversion[Double, COut]) extends SFXControl[COut, Double, Slider](control)(using inConversion, outConversion) { 
    control.value.onChange((_, _, newVal) => updateProperty(newVal.doubleValue()))

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
            case Right(nv) =>
                control.value() = nv
                clearError()
            case Left(msg) => 
                showError(msg)

        }
    })

    updateProperty(control.value())
}