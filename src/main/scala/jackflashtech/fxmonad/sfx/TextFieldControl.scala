package jackflashtech.fxmonad.sfx

import scalafx.beans.property.Property
import scalafx.scene.control.TextField
import jackflashtech.fxmonad.TextFieldProxy
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import scalafx.scene.control.Tooltip
import scalafx.beans.property.IntegerProperty
import scalafx.beans.property.StringProperty
import scalafx.application.Platform
import jackflashtech.fxmonad.SFXControl

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

class TextFieldControlInt(control: TextField = new TextFieldProxy())(using inConversion: Conversion[Int, String], outConversion: Conversion[String, Int]) extends TextFieldControl[Int](new IntegerProperty(), control)(using inConversion, outConversion)
class TextFieldControlString(control: TextField = new TextFieldProxy())(using inConversion: Conversion[String, String], outConversion: Conversion[String, String]) extends TextFieldControl[String](StringProperty(""), control)(using inConversion, outConversion)