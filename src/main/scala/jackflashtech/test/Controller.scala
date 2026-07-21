package jackflashtech.test

import jackflashtech.fxmonad.Control
import jackflashtech.fxmonad.FXMonad
import scala.annotation.experimental
import javafx.fxml.FXML
import jackflashtech.fxmonad._
import jackflashtech.fxmonad.sfx.TextFieldControlString
import jackflashtech.fxmonad.sfx.LabelControlString
import scalafx.scene.paint.Color

/** A controller that uses the fxmonad system in conjunction with the
  * ScalaFX/JavaFX system. This is used for testing use cases of the tools.
  */
@experimental
class Controller {

  @FXMonad("colorControl")
  lazy val color: Control[Color] = ???

  @FXMonad("colorOutput")
  lazy val colorOut: Control[String] = ???

  @FXMonad("ageControl")
  lazy val age: Control[Int] = ???

  @FXMonad("ageOutput")
  lazy val ageDisplay: Control[String] = ???
  
  @FXMonad("temperatureSlider")
  lazy val temperatureControl: Control[Double] = ???

  // TODO: Note to self, before putting in the check, I had defined both the Control and the JavaFX id as "temperatureDisplay" and it worked. I don't know how the generated code didn't produce two different variables named "temperatureDisplay", let alone how JavaFX correctly initialized the class.
  @FXMonad("temperatureOutput")
  lazy val temperatureDisplay: Control[String] = ???

  @FXMonad("textBox")
  lazy val textBoxC: Control[Int] = ???

  @FXMonad("textBox2")
  lazy val textBoxC2: Control[String] = ???

  @FXMonad("textBox3")
  lazy val textBoxC3: Control[String] = ???

  @FXMonad("textBox4")
  lazy val textBoxC4: Control[String] = ???

  @FXMonad("textBox5")
  lazy val textBoxC5: Control[String] = ???

  @FXMonad("textBox6")
  lazy val textBoxC6: Control[String] = ???

  @FXMonad("checkBox1")
  lazy val checkBoxC1: Control[Boolean] = ???

  @FXMonad("checkBox2")
  lazy val checkBoxC2: Control[Int] = ???

  @FXMonad("checkBox3")
  lazy val checkBoxC3: Control[String] = ???

  @FXML
  def initialize() = {
    import jackflashtech.fxmonad.Control.given

    textBoxC3(textBoxC, textBoxC2) = {
      (shouldBeInt: Int, shouldBeString: String) =>
        val newC = TextFieldControlString(s"${shouldBeInt.toString()} and ${shouldBeString}")
        if (shouldBeInt > 10) {
          newC.control.styleClass.add("emphasis")
        } else { // TODO: This doesn't work right when removing a style, because the new control doesn't have the style, so the remove action is by index and reports removing at index -1, but the control to update does have the class and it doesn't get removed. It would be nice if the new control was initialized with the new control. Maybe this function should take a (using control) and any new control automatically wraps the old control? Except I want it to be able to change the control.
          newC.control.styleClass.removeAll("emphasis")
        }
          
        newC
    }

    textBoxC5(textBoxC4) = { (shouldBeString: String) =>
      TextFieldControlString(s"${shouldBeString} and Stuff")
    }

    textBoxC6(checkBoxC1, checkBoxC2, checkBoxC3) = {
      (shouldBeBoolean: Boolean, shouldBeInt: Int, shouldBeString: String) =>
        TextFieldControlString(s"Box1: ${shouldBeBoolean}, Box2: ${shouldBeInt}, and Box3: ${shouldBeString}")
    }

    ageDisplay(age) = {
      (age: Int) =>
        TextFieldControlString(s"Age: ${age}")
    }

    temperatureDisplay(temperatureControl) = {
      (temperature: Double) =>
        if(temperature > 15.0) {
          LabelControlString(s"Temperature: ${temperature}", new scalafx.scene.control.Label())
        } else {
          TextFieldControlString(s"Temperature: ${temperature}", new scalafx.scene.control.TextField())
        }
    }
    colorOut(color) = {
      (color: Color) =>
        TextFieldControlString(color.toString)
    }
  }
}
