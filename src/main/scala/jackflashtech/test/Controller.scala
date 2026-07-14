package jackflashtech.test

import jackflashtech.fxmonad.Control
import jackflashtech.fxmonad.FXMonad
import scala.annotation.experimental
import javafx.fxml.FXML
import jackflashtech.fxmonad._
import jackflashtech.fxmonad._
import jackflashtech.fxmonad.sfx.TextFieldControlString

/** A controller that uses the fxmonad system in conjunction with the
  * ScalaFX/JavaFX system. This is used for testing use cases of the tools.
  */
@experimental
class Controller {
  @FXMonad("textBox", "javafx.scene.control.TextField")
  lazy val textBoxC: Control[Int, ?] = ???

  @FXMonad("textBox2", "javafx.scene.control.TextField")
  lazy val textBoxC2: Control[String, ?] = ???

  @FXMonad("textBox3", "javafx.scene.control.TextField")
  lazy val textBoxC3: Control[String, ?] = ???

  @FXMonad("textBox4", "javafx.scene.control.TextField")
  lazy val textBoxC4: Control[String, ?] = ???

  @FXMonad("textBox5", "javafx.scene.control.TextField")
  lazy val textBoxC5: Control[String, ?] = ???

  @FXMonad("textBox6", "javafx.scene.control.TextField")
  lazy val textBoxC6: Control[String, ?] = ???

  @FXMonad("checkBox1", "javafx.scene.control.CheckBox")
  lazy val checkBoxC1: Control[Boolean, ?] = ???

  @FXMonad("checkBox2", "javafx.scene.control.CheckBox")
  lazy val checkBoxC2: Control[Int, ?] = ???

  @FXMonad("checkBox3", "javafx.scene.control.CheckBox")
  lazy val checkBoxC3: Control[String, ?] = ???

  @FXML
  def initialize() = {
    import jackflashtech.fxmonad.Control.given

    textBoxC3(textBoxC, textBoxC2) = {
      (shouldBeInt: Int, shouldBeString: String) =>
        val newC = TextFieldControlString()
        if (shouldBeInt > 10) {
          newC.control.styleClass.add("emphasis")
        } else { // TODO: This doesn't work right when removing a style, because the new control doesn't have the style, so the remove action is by index and reports removing at index -1, but the control to update does have the class and it doesn't get removed. It would be nice if the new control was initialized with the new control. Maybe this function should take a (using control) and any new control automatically wraps the old control? Except I want it to be able to change the control.
          newC.control.styleClass.removeAll("emphasis")
        }
        newC.defaultProperty() =
          s"${shouldBeInt.toString()} and ${shouldBeString}"
        newC
    }
    textBoxC5(textBoxC4) = { (shouldBeString: String) =>
      val newC = TextFieldControlString()
      newC.defaultProperty() = s"${shouldBeString} and Stuff"
      newC
    }

    textBoxC6(checkBoxC1, checkBoxC2, checkBoxC3) = {
      (shouldBeBoolean: Boolean, shouldBeInt: Int, shouldBeString: String) =>
        val newC = TextFieldControlString()
        newC.defaultProperty() =
          s"Box1: ${shouldBeBoolean}, Box2: ${shouldBeInt}, and Box3: ${shouldBeString}"
        newC
    }
  }
}
