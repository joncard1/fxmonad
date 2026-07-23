package fxmonad.sfx

import scalafx.beans.property.BooleanProperty
import scalafx.beans.property.IntegerProperty
import scalafx.beans.property.StringProperty
import scalafx.scene.control.CheckBox
import scalafx.beans.property.Property
import scalafx.scene.control.Tooltip
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import fxmonad.Control
import fxmonad.SFXControl

abstract class CheckBoxControl[COut](override val defaultProperty: Property[COut, ?], control: CheckBox)(using inConversion: Conversion[COut, Boolean], outConversion: Conversion[Boolean, COut]) extends SFXControl[COut, Boolean, CheckBox](control)(using inConversion, outConversion) {
    override protected[fxmonad] def showError(errorMsg: String): Unit = control.tooltip() = Tooltip(errorMsg)
    override protected[fxmonad] def clearError(): Unit = control.tooltip() = null

    control.selected.onChange(  (_, _, newVal) => updateProperty(newVal))

    defaultProperty.onChange((_, _, newVal) => {
        Try(inConversion(defaultProperty())) match {
            case Success(nv) =>
                control.selected() = nv
                clearError()
            case Failure(err) =>
                showError(err.getMessage)
        }
    })

    updateProperty(control.selected())
}

class CheckBoxControlBoolean(control: CheckBox)(using inConversion: Conversion[Boolean, Boolean], outConversion: Conversion[Boolean, Boolean]) extends CheckBoxControl[Boolean](BooleanProperty(false), control)(using inConversion, outConversion)
class CheckBoxControlInt(control: CheckBox)(using inConversion: Conversion[Int, Boolean], outConversion: Conversion[Boolean, Int]) extends CheckBoxControl[Int](new IntegerProperty(), control)(using inConversion, outConversion)
class CheckBoxControlString(control: CheckBox)(using inConversion: Conversion[String, Boolean], outConversion: Conversion[Boolean, String]) extends CheckBoxControl[String](new StringProperty(), control)(using inConversion, outConversion)