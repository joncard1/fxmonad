package fxmonad.sfx

import scalafx.scene.control.Label
import fxmonad.SFXControl
import scalafx.beans.property.Property
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import scalafx.application.Platform
import fxmonad.LabelProxy
import scalafx.beans.property.StringProperty

abstract class LabelControl[COut](override val defaultProperty: Property[COut, ?], control: Label = new LabelProxy())(using inConversion: Conversion[COut, String], outConversion: Conversion[String, COut]) extends SFXControl[COut, String, Label](control)(using inConversion, outConversion) {
  // Not bothering to subscribe to property changes because it's a read-only control

  override protected[fxmonad] def clearError(): Unit = {}
  override protected[fxmonad] def showError(errorMsg: String): Unit = {}

  defaultProperty.onChange((_, _, newVal) => {
    Try(inConversion(defaultProperty())) match {
      case Success(null) => showError("This control was given a value that converted to null")
      case Success(nv) =>
        control.text() = nv
        clearError()
      case Failure(exception) => showError(exception.getMessage())
    }
  })
}

class LabelControlString(control: Label = new LabelProxy())(using inConversion: Conversion[String, String], outConversion: Conversion[String, String]) extends LabelControl[String](new StringProperty(), control)(using inConversion, outConversion) {
  def this(initialValue: String)(using inConversion: Conversion[String, String], outConversion: Conversion[String, String]) = {
    this()(using inConversion, outConversion)
    this.defaultProperty() = initialValue
  }

  def this(initialValue: String, control: Label)(using inConversion: Conversion[String, String], outConversion: Conversion[String, String]) = {
    this(control)(using inConversion, outConversion)
    this.defaultProperty() = initialValue
  }
}