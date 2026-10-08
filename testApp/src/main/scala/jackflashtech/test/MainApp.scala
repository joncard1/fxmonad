package jackflashtech.test

import scalafx.application.JFXApp3
import javafx.fxml.FXMLLoader
import javafx.{scene => jfxs}
import scalafx.scene.Scene
import scalafx.Includes._
import scala.annotation.experimental
import fxmonad.Control
import scalafx.scene.paint.Color
import fxmonad.sfx._

/** A simple ScalaFX application used for testing the use cases for this
  * project.
  */
@experimental
object MainApp extends JFXApp3 {
  override def start(): Unit = {
    Control.registerControl(
      classOf[Color],
      { case c: javafx.scene.control.ColorPicker =>
        ColorPickerControlColor(scalafx.scene.control.ColorPicker(c))
      }
    )

    stage = new JFXApp3.PrimaryStage {
      val viewClass = getClass.getResource("main-screen.fxml")
      println(viewClass.toString())
      val loader = new FXMLLoader(viewClass)
      val root: jfxs.Parent = loader.load()
      loader.getController[Controller]()
      scene = new Scene(root) {
        stylesheets = List(getClass.getResource("styles.css").toExternalForm)

      }
    }

    stage.setMinWidth(500)
    stage.setMinHeight(250)
  }
}
