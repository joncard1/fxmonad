package jackflashtech.test

import javafx.fxml.FXML
import fxmonad.Control
import scalafx.beans.property.Property
import fxmonad.FXMonad
import fxmonad.FXEmitter
import fxmonad.Emitter
import fxmonad.ReceivesEvents
import scalafx.application.ConditionalFeature.FXML
import scala.util.Using
import scala.annotation.experimental
import scalafx.beans.property.StringProperty

sealed case class Click()

@experimental
class Subcontroller extends Control[String] with ReceivesEvents[Click] {

  @FXMonad("textbox")
  lazy val myName: Control[String] = ???

  @FXEmitter("button")
  lazy val myButton: Emitter[Click] = ???

  override val defaultProperty: Property[String, ?] = StringProperty("")

  // TODO: define showError and clearError. Is this the kind of thing I should suppress in a good implementation? Is this why I should have a superclass of this defined in the library?
  override protected def showError(errorMsg: String): Unit = ???

  override protected def clearError(): Unit = ???

  @FXML
  def initialize() = {
    Using.resource(eventProcessor) { ep =>
      ep.registerHandler { 
        case Click() =>
          defaultProperty() = myName()
      }
      myButton.onAction(_ => Click())

    }
  }
}
