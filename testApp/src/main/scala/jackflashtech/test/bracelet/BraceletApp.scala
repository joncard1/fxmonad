package jackflashtech.test.bracelet

import scalafx.application.JFXApp3
import javafx.fxml.FXMLLoader
import javafx.{scene => jfxs}
import scalafx.scene.Scene
import scalafx.Includes._
import scala.annotation.experimental
import fxmonad.{Control, ControlContainer}
import fxmonad.sfx._
import scala.reflect.ClassTag

/** A small ScalaFX application exercising the "bracelet" test controller. */
@experimental
object BraceletApp extends JFXApp3 {
  override def start(): Unit = {
    import IntensityInstances.given
    import BraceletController.given

    Control.registerControl(
      classOf[Intensity],
      {
        case c: javafx.scene.control.Label =>
          ControlContainer(
            LabelControl[Intensity](new scalafx.scene.control.Label(c))
          )
        case c: javafx.scene.control.Slider =>
          ControlContainer(
            SliderControl[Intensity](scalafx.scene.control.Slider(c))
          )
        case c: fxmonad.ControlPane[Intensity] =>
          c.initializeContainer(using summon[ClassTag[Intensity]])
          c
      }
    )

    Control.registerControl(
      classOf[BraceletController.DisplayMode],
      { case c: javafx.scene.control.ToggleGroup =>
        ControlContainer(
          ToggleGroupControl[BraceletController.DisplayMode](
            scalafx.scene.control.ToggleGroup(c)
          )
        )
      }
    )

    stage = new JFXApp3.PrimaryStage {
      val viewClass = getClass.getResource("bracelet-screen.fxml")
      val loader = new FXMLLoader(viewClass)
      val root: jfxs.Parent = loader.load()
      loader.getController[BraceletController]()
      scene = new Scene(root)
    }

    stage.setMinWidth(300)
    stage.setMinHeight(250)

    stage.onCloseRequest = _ => {
      BraceletHidControl.stop()
    }
  }
}
