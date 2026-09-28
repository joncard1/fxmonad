package fxmonad.sfx

import fxmonad.Emitter
import fxmonad.EventProcessor
import javafx.event.ActionEvent
import javafx.event.EventHandler

/** An [[Emitter]] backed by a JavaFX [[javafx.scene.control.Button]]. Its
  * `onAction` installs the button's action handler, so each click builds a
  * message and dispatches it to the ambient [[EventProcessor]].
  *
  * @param control
  *   the underlying JavaFX button (typically supplied by the `@FXEmitter` macro
  *   from an FXML-injected node).
  */
class ButtonEmitter[M](val control: javafx.scene.control.Button)
    extends Emitter[M] {

  override def onAction(
      f: ActionEvent => M
  )(using processor: EventProcessor[M]): Unit =
    control.setOnAction(new EventHandler[ActionEvent] {
      override def handle(event: ActionEvent): Unit =
        processor.dispatch(f(event))
    })
}
