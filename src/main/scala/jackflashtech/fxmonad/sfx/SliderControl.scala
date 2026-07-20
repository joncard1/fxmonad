package jackflashtech.fxmonad.sfx

import scalafx.beans.property.Property
import scalafx.scene.control.Slider
import jackflashtech.fxmonad.SFXControl
import scalafx.concurrent.Worker.State.Succeeded
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import jackflashtech.fxmonad.SliderProxy
import scalafx.beans.property.IntegerProperty
import scalafx.application.Platform
import scalafx.scene.control.Tooltip
import scalafx.beans.property.DoubleProperty

abstract class SliderControl[COut](override val defaultProperty: Property[COut, ?], control: Slider = new SliderProxy())(using inConversion: Conversion[COut, Double], outConversion: Conversion[Double, COut]) extends SFXControl[COut, Double, Slider](control)(using inConversion, outConversion) { 
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
        Try(inConversion(defaultProperty())) match {
            case Success(nv) =>
                control.value() = nv
                clearError()
            case Failure(exception) => 
                showError(exception.getMessage())

        }
    })

    updateProperty(control.value())
}

class SliderControlInt(control: Slider = new SliderProxy())(using inConversion: Conversion[Int, Double], outConversion: Conversion[Double, Int]) extends SliderControl[Int](new IntegerProperty(), control)(using inConversion, outConversion) {
    def this(initialValue: Int)(using inConversion: Conversion[Int, Double], outConversion: Conversion[Double, Int]) = {
        this()
        this.defaultProperty() = initialValue
    }

    def this(initialValue: Int, control: Slider)(using inConversion: Conversion[Int, Double], outConversion: Conversion[Double, Int]) = {
        this(control)
        this.defaultProperty() = initialValue
    }
}
class SliderControlDouble(control: Slider = new SliderProxy())(using inConversion: Conversion[Double, Double], outConversion: Conversion[Double, Double]) extends SliderControl[Double](new DoubleProperty(), control)(using inConversion, outConversion) {
    def this(initialValue: Double)(using inConversion: Conversion[Double, Double], outConversion: Conversion[Double, Double]) = {
        this()(using inConversion, outConversion)
        this.defaultProperty() = initialValue
    }

    def this(initialValue: Double, control: Slider)(using inConversion: Conversion[Double, Double], outConversion: Conversion[Double, Double]) = {
        this(control)(using inConversion, outConversion)
        this.defaultProperty() = initialValue
    }
}