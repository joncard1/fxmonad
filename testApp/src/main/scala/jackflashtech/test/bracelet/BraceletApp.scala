package jackflashtech.test.bracelet

import scalafx.application.JFXApp3
import javafx.fxml.FXMLLoader
import javafx.{scene => jfxs}
import scalafx.scene.Scene
import scalafx.Includes._
import scala.annotation.experimental
import fxmonad.{ControlContainer, FXMonad, PropertyConstructor}
import fxmonad.sfx._
import scalafx.beans.property.BooleanProperty

/** A small ScalaFX application exercising the "bracelet" test controller. */
@experimental
object BraceletApp extends JFXApp3 {
  override def start(): Unit = {
    import fxmonad.Control.given
    import IntensityInstances.given

    FXMonad.lookups.getAndUpdate { lookups =>
      val withRadioButton = lookups + (classOf[Boolean] -> ({
        case c: javafx.scene.control.RadioButton =>
          ControlContainer(
            new BooleanProperty(),
            RadioButtonControl(scalafx.scene.control.RadioButton(c))
          )
      } :: lookups.getOrElse(classOf[Boolean], List())))

      withRadioButton + (classOf[Intensity] -> ({
        case c: javafx.scene.control.TextField =>
          ControlContainer(
            summon[PropertyConstructor[Intensity]](),
            TextFieldControl[Intensity](scalafx.scene.control.TextField(c))
          )
        case c: javafx.scene.control.Slider =>
          ControlContainer(
            summon[PropertyConstructor[Intensity]](),
            SliderControl[Intensity](scalafx.scene.control.Slider(c))
          )
      } :: withRadioButton.getOrElse(classOf[Intensity], List())))
    }

    stage = new JFXApp3.PrimaryStage {
      val viewClass = getClass.getResource("bracelet-screen.fxml")
      val loader = new FXMLLoader(viewClass)
      val root: jfxs.Parent = loader.load()
      loader.getController[BraceletController]()
      scene = new Scene(root)
    }
  }
}
