package jackflashtech.test

import scalafx.application.JFXApp3
import javafx.fxml.FXMLLoader
import javafx.{fxml => jfxf, scene => jfxs}
import scalafx.scene.Scene
import scalafx.Includes._
import scala.annotation.experimental

/** A simple ScalaFX application used for testing the use cases for this
  * project.
  */
@experimental
object MainApp extends JFXApp3 {
  override def start(): Unit = {
    stage = new JFXApp3.PrimaryStage {
      val viewClass = getClass.getResource("main-screen.fxml")
      println(viewClass.toString())
      val loader = new FXMLLoader(viewClass)
      val root: jfxs.Parent = loader.load()
      val controller = loader.getController[Controller]()
      scene = new Scene(root) {
        stylesheets = List(getClass.getResource("styles.css").toExternalForm)

      }
    }
  }
}
