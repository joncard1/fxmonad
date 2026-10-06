package jackflashtech.test.bracelet

import fxmonad._
import fxmonad.sfx._
import scala.annotation.experimental
import javafx.fxml.FXML
import scalafx.beans.property.ObjectProperty

import IntensityInstances.given
import scalafx.scene.control.ToggleGroup
import scala.util.Try

object BraceletController {
  enum DisplayMode {
    case onscreen, bracelet
  }

  given PropertyConstructor[DisplayMode] = () =>
    ObjectProperty[DisplayMode](DisplayMode.onscreen)
  given Conversion[DisplayMode, Object] = (x: DisplayMode) => Right(x.toString())
  given Conversion[Object, DisplayMode] = (x: Object) =>
    x match {
      case dm: String => Try { DisplayMode.valueOf(dm) }.toEither match {
        case Left(e) => Left(e.toString())
        case Right(x) => Right(x)
      }
      case _          =>
        Left(
          s"Was expecting a BraceletApp.DisplayMode, but was some other kind of object. ${x.toString()}"
        )
    }
}

/** Controller for the bracelet test app. Shows 6 intensity displays fed either
  * by the physical "Bracelet" HID device or by an on-screen slider panel,
  * selected via a pair of radio buttons.
  */
@experimental
class BraceletController {
  // TODO: Can't use the FXMonad macro here because it creates the private var modeGroup with type javafx.scene.control.Control, and ToggleGroup isn't a Control. Not sure what to do about this. Maybe provide an override with the @FXML variable type as a second argument to FXMonad?
  @FXML
  private var modeGroup: javafx.scene.control.ToggleGroup = null
  lazy val displayMode: Control[BraceletController.DisplayMode] =
    ControlContainer(
      summon[PropertyConstructor[BraceletController.DisplayMode]](),
      ToggleGroupControl(
        scalafx.scene.control.ToggleGroup(modeGroup)
      )
    )

  @FXMonad("braceletRadio")
  lazy val braceletMode: Control[Boolean] = ???

  @FXMonad("onScreenRadio")
  lazy val onScreenMode: Control[Boolean] = ???

  @FXMonad("displayControl1")
  lazy val display1: Control[Intensity] = ???

  @FXMonad("displayControl2")
  lazy val display2: Control[Intensity] = ???

  // The included slider-panel.fxml's root node and its controller.
  @FXML
  private var sliderPanel: javafx.scene.Node = null

  @FXML
  private var sliderPanelController: SliderPanelController = null

  /** Placeholder for the physical "Bracelet" HID device. */
  lazy val hidControl1: Control[Intensity] =
    BraceletHidControl("Bracelet", 0, 1)

  // TODO: Maybe configure the buttons on the other hand as emitters
  lazy val hidControl2: Control[Intensity] =
    BraceletHidControl("Bracelet", 3, 4)

  // Not backed by any widget: tracks whichever source (HID or slider) is
  // currently selected for the matching display.
  def intensityContainer(
      initialControl: Control[Intensity]
  ): ControlContainer[Intensity] =
    ControlContainer(
      ObjectProperty[Intensity](Intensity.min),
      initialControl
    )

  @FXML
  def initialize(): Unit = {

    def sourceFor(
        slider: Control[Intensity],
        hid: Control[Intensity]
    ): BraceletController.DisplayMode => Control[Intensity] =
      displayMode =>
        if (displayMode == BraceletController.DisplayMode.bracelet) hid
        else slider

    sliderPanelController.intensity1(displayMode) =
      sourceFor(sliderPanelController.slider1, hidControl1)
    sliderPanelController.intensity2(displayMode) =
      sourceFor(sliderPanelController.slider2, hidControl2)

    display1(sliderPanelController.intensity1) = { i =>
      LabelControl[Intensity](i)
    }
    display2(sliderPanelController.intensity2) = { i =>
      LabelControl[Intensity](i)
    }
  }
}
