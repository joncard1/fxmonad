package fxmonad.sfx

import scalafx.beans.property.Property
import scalafx.scene.control.TextField
import fxmonad.TextFieldProxy
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import scalafx.scene.control.Tooltip
import scalafx.beans.property.IntegerProperty
import scalafx.beans.property.StringProperty
import scalafx.application.Platform
import fxmonad.SFXControl

abstract class TextFieldControl[COut](override val defaultProperty: Property[COut, ?], control: TextField = new TextFieldProxy())(using inConversion: Conversion[COut, String], outConversion: Conversion[String, COut]) extends SFXControl[COut, String, TextField](control)(using inConversion, outConversion) {

    control.text.onChange(  (_, _, newVal) => updateProperty(newVal))

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
            case Success(null) =>
                showError("Property was set to null")
            case Success(nv) =>
                control.text() = nv
                clearError()
            case Failure(err) =>
                showError(err.getMessage())
        }
    })

    updateProperty(control.text())
}

class TextFieldControlInt(control: TextField = new TextFieldProxy())(using inConversion: Conversion[Int, String], outConversion: Conversion[String, Int]) extends TextFieldControl[Int](new IntegerProperty(), control)(using inConversion, outConversion) {
    def this(initialValue: Int)(using inConversion: Conversion[Int, String], outconversion: Conversion[String, Int]) = {
        this()
        this.defaultProperty() = initialValue
    }

    def this(initialValue: Int, control: TextField)(using inConversion: Conversion[Int, String], outConversion: Conversion[String, Int]) = {
        this(control)
        this.defaultProperty() = initialValue
    }

}
class TextFieldControlString(control: TextField = new TextFieldProxy())(using inConversion: Conversion[String, String], outConversion: Conversion[String, String]) extends TextFieldControl[String](StringProperty(""), control)(using inConversion, outConversion) {
    def this(initialValue: String)(using inConversion: Conversion[String, String], outConversion: Conversion[String, String]) = {
        this()(using inConversion, outConversion)
        this.defaultProperty() = initialValue
    }

    def this(initialValue: String, control: TextField)(using inConversion: Conversion[String, String], outConversion: Conversion[String, String]) = {
        this(control)(using inConversion, outConversion)
        this.defaultProperty() = initialValue
    }
}