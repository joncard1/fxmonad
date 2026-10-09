package fxmonad.sfx

import java.util.concurrent.Executor
import scalafx.application.Platform

/** An [[Executor]] that runs tasks on the JavaFX Application Thread: inline if
  * already on it, otherwise queued with `Platform.runLater` (which preserves
  * submission order).
  *
  * Controls whose value originates on a non-JavaFX thread (e.g. an HID device
  * callback) can take a plain `Executor` for publishing their value changes and
  * be handed this one, so that they never reference JavaFX themselves while
  * every property write, and therefore every downstream listener, binder, and
  * widget update, still happens on the FX thread.
  */
object FXThreadExecutor extends Executor {
  override def execute(task: Runnable): Unit =
    if (Platform.isFxApplicationThread) task.run()
    else Platform.runLater(task)
}
