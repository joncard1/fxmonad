package jackflashtech.test.bracelet

import fxmonad._
import fxmonad.sfx.TextFieldControl
import scala.annotation.experimental
import javafx.fxml.FXML
import scalafx.beans.property.ObjectProperty

import IntensityInstances.given

/** Controller for the bracelet test app. Shows 6 intensity displays fed either
  * by the physical "Bracelet" HID device or by an on-screen slider panel,
  * selected via a pair of radio buttons.
  */
@experimental
class BraceletController {

  @FXMonad("braceletRadio")
  lazy val braceletMode: Control[Boolean] = ???

  @FXMonad("onScreenRadio")
  lazy val onScreenMode: Control[Boolean] = ???

  @FXMonad("displayControl1")
  lazy val display1: Control[Intensity] = ???

  @FXMonad("displayControl2")
  lazy val display2: Control[Intensity] = ???

  @FXMonad("displayControl3")
  lazy val display3: Control[Intensity] = ???

  @FXMonad("displayControl4")
  lazy val display4: Control[Intensity] = ???

  @FXMonad("displayControl5")
  lazy val display5: Control[Intensity] = ???

  @FXMonad("displayControl6")
  lazy val display6: Control[Intensity] = ???

  // Not backed by any widget: tracks whichever source (HID or slider) is
  // currently selected for the matching display.
  def intensityContainer(): ControlContainer[Intensity] =
    new ControlContainer(
      ObjectProperty[Intensity](Intensity.min),
      TextFieldControl[Intensity]()
    )

  lazy val intensity1: ControlContainer[Intensity] = intensityContainer()
  lazy val intensity2: ControlContainer[Intensity] = intensityContainer()
  lazy val intensity3: ControlContainer[Intensity] = intensityContainer()
  lazy val intensity4: ControlContainer[Intensity] = intensityContainer()
  lazy val intensity5: ControlContainer[Intensity] = intensityContainer()
  lazy val intensity6: ControlContainer[Intensity] = intensityContainer()

  // The included slider-panel.fxml's root node and its controller.
  @FXML
  private var sliderPanel: javafx.scene.Node = null

  @FXML
  private var sliderPanelController: SliderPanelController = null

  /** Placeholder for the physical "Bracelet" HID device. */
  lazy val hidControl: Control[Intensity] = new BraceletHidControl("Bracelet")

  @FXML
  def initialize(): Unit = {
    def sourceFor(slider: Control[Intensity]): Boolean => Control[Intensity] =
      isBracelet => if (isBracelet) hidControl else slider

    intensity1(braceletMode) = sourceFor(sliderPanelController.slider1)
    intensity2(braceletMode) = sourceFor(sliderPanelController.slider2)
    intensity3(braceletMode) = sourceFor(sliderPanelController.slider3)
    intensity4(braceletMode) = sourceFor(sliderPanelController.slider4)
    intensity5(braceletMode) = sourceFor(sliderPanelController.slider5)
    intensity6(braceletMode) = sourceFor(sliderPanelController.slider6)

    display1(intensity1) = { i => TextFieldControl[Intensity](i) }
    display2(intensity2) = { i => TextFieldControl[Intensity](i) }
    display3(intensity3) = { i => TextFieldControl[Intensity](i) }
    display4(intensity4) = { i => TextFieldControl[Intensity](i) }
    display5(intensity5) = { i => TextFieldControl[Intensity](i) }
    display6(intensity6) = { i => TextFieldControl[Intensity](i) }

    def updateSliderPanelVisibility(isBracelet: Boolean): Unit = {
      sliderPanel.setVisible(!isBracelet)
      sliderPanel.setManaged(!isBracelet)
    }

    braceletMode.defaultProperty.onChange((_, _, _) =>
      updateSliderPanelVisibility(braceletMode())
    )
    updateSliderPanelVisibility(braceletMode())
  }
}
