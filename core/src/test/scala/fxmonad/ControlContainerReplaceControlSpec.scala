package fxmonad

import scalafx.application.Platform
import scalafx.beans.property.IntegerProperty
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javafx.scene.layout.VBox
import org.testfx.api.FxToolkit
import fxmonad.sfx._
import fxmonad.Control.given

/** Exercises the case matrix `ControlContainer#replaceControl` uses to
  * reconcile a newly-produced `Control` against the one it's currently
  * wrapping. See the doc comment on `replaceControl` for the case list this
  * mirrors.
  */
class ControlContainerReplaceControlSpec extends munit.FunSuite {

  override def beforeEach(context: BeforeEach): Unit = {
    FxToolkit.registerPrimaryStage(): Unit
  }
  override def afterEach(context: AfterEach): Unit = {
    FxToolkit.cleanupStages()
  }

  // TODO: I added Platform.runLater to SFCControlContainer because, in my implementation of BraceletHidControl, the Bluetooth event thread was updating ScalaFX properties, which updated JavaFX properties, which meant updates to the JavaFX system had to be marshalled to the JavaFX thread. That caused problems here because this assumes that the code here doesn't change threads. I'm not sure what the solution is: either changes to properties need to be made on the JavaFX Application Thread (which I don't think is required) or I need a definite decision on when changes need to be marshalled to the Java Application Thread, which I'm not sure I know how to make. SFXControlContainer seems like the correct place, but I'm not 100% sure.
  // TODO: I wonder if calling body, then adding the latch to runLater, then waiting on the latch will work, but I'm not sure if things added to runLater are guaranteed to run in order?
  //  I tried that, and the "same-class proxy" test passed, but the others didn't. Must be a different issue, but commenting out Platform.runLater in SFXControlContainer fixed those.
  //  The "new control has no widget" test failed in either case; that's a known different issue.
  //  Actually, that might not be the issue, because there's already a call to Platform.runLater in swapWidget, so why did the HID thread run into problems? Maybe I need to take that call back out and debug that differently.
  /** Runs `body` on the FX thread and waits for it to finish. Use when
    * `replaceControl` isn't expected to schedule any scene-graph mutation
    * (nothing to flush via a second `Platform.runLater`).
    */
  private def onFx(body: => Unit): Unit = {
    val latch = new CountDownLatch(1)
    var error: Option[Throwable] = None
    Platform.runLater(() => {
      try { body }
      catch { case t: Throwable => error = Some(t) }
      finally { latch.countDown() }
    })
    assert(latch.await(30, TimeUnit.SECONDS), "FX thread callback never ran")
    error.foreach(throw _)
  }

  /** Runs `act` on the FX thread, then runs `assertAfterFlush` in a
    * subsequently-queued FX callback - so any `Platform.runLater` scheduled by
    * `replaceControl` during `act` (e.g. a Pane child swap) has already run by
    * the time `assertAfterFlush` executes.
    */
  private def onFxThenAfterFlush(
      act: => Unit
  )(assertAfterFlush: => Unit): Unit = {
    val latch = new CountDownLatch(1)
    var error: Option[Throwable] = None
    Platform.runLater(() => {
      try {
        act
        Platform.runLater(() => {
          try { assertAfterFlush }
          catch { case t: Throwable => error = Some(t) }
          finally { latch.countDown() }
        })
      } catch {
        case t: Throwable =>
          error = Some(t)
          latch.countDown()
      }
    })
    assert(latch.await(5, TimeUnit.SECONDS), "FX thread callback never ran")
    error.foreach(throw _)
  }

  test(
    "new control has no widget: removes the old widget and tracks the value live"
  ) {
    var pane: VBox = null
    onFxThenAfterFlush {
      pane = new VBox()
      val oldWidget =
        TextFieldControl[Int](5, new scalafx.scene.control.TextField())
      pane.getChildren().add(oldWidget.control.delegate)
      val container =
        ControlContainer[Int](new IntegerProperty(), oldWidget)

      // A ControlContainer isn't itself an SFXControl, matching the sliders in
      // BraceletController, which are Control[Intensity] backed by one.
      val newValueOnly = ControlContainer[Int](
        new IntegerProperty(),
        TextFieldControl[Int](42)
      )
      container.replaceControl(newValueOnly)

      assertEquals(container(), 42)
      newValueOnly() = 99
      assertEquals(container(), 99)
    } {
      assertEquals(pane.getChildren().size(), 0)
    }
  }

  test(
    "old control has no widget: value is still tracked live even though placement is unimplemented"
  ) {
    onFx {
      val initialNonWidget = ControlContainer[Int](
        new IntegerProperty(),
        TextFieldControl[Int](1)
      )
      val container =
        ControlContainer[Int](new IntegerProperty(), initialNonWidget)

      // TODO: I changed this test. if both the old and the new widget are proxies, there's nothing to track.
      val newWidgetControl =
        TextFieldControl[Int](7, new scalafx.scene.control.TextField())
      container.replaceControl(newWidgetControl)
      assertEquals(container(), 7)

      newWidgetControl() = 21
      assertEquals(container(), 21)
    }
  }

  test(
    "same-class proxy: replays its changes onto the existing live widget instead of swapping it"
  ) {
    onFx {
      val realTextField = new scalafx.scene.control.TextField()
      val oldWidget = TextFieldControl[Int](5, realTextField)
      val container =
        ControlContainer[Int](new IntegerProperty(), oldWidget)

      // Backed by a default TextFieldProxy - the shape a binding function
      // like `TextFieldControl(newValue)` normally returns.
      val proxyControl = TextFieldControl[Int](77)
      container.replaceControl(proxyControl)

      assertEquals(realTextField.text(), "77")
      assertEquals(container(), 77)
    }
  }

  test("mismatched-class proxy: falls back to tracking the value only") {
    onFx {
      val realTextField = new scalafx.scene.control.TextField()
      val oldWidget = TextFieldControl[Int](5, realTextField)
      val container =
        ControlContainer[Int](new IntegerProperty(), oldWidget)

      // Backed by a default CheckBoxProxy: wrong shape to replay onto a TextField.
      val mismatchedProxyControl = CheckBoxControl[Int](1)
      container.replaceControl(mismatchedProxyControl)

      assertEquals(container(), 1)
      // No swap happened: the old (still-live) TextField just displays the
      // copied value, rather than being replaced by a different widget.
      assertEquals(realTextField.text(), "1")
    }
  }

  test(
    "genuinely new widget: swaps the live node in its parent Pane at the same index"
  ) {
    var pane: VBox = null
    var newLabelControl: LabelControl[Int] = null
    onFxThenAfterFlush {
      pane = new VBox()
      val before = new scalafx.scene.control.Label("before")
      val realTextField = new scalafx.scene.control.TextField()
      val after = new scalafx.scene.control.Label("after")
      pane
        .getChildren()
        .addAll(before.delegate, realTextField.delegate, after.delegate)

      val oldWidget = TextFieldControl[Int](5, realTextField)
      val container =
        ControlContainer[Int](new IntegerProperty(), oldWidget)

      // A genuine (non-proxy) widget, as in the temperatureDisplay Label/TextField swap example.
      newLabelControl = LabelControl[Int](9, new scalafx.scene.control.Label())
      container.replaceControl(newLabelControl)

      assertEquals(container(), 9)
      newLabelControl() = 30
      assertEquals(container(), 30)
    } {
      assertEquals(pane.getChildren().size(), 3)
      assertEquals(pane.getChildren().get(1), newLabelControl.control.delegate)
    }
  }

  test(
    "widget with no parent Pane: swaps the wrapped control without touching any scene graph"
  ) {
    onFx {
      // Never added to any Pane.
      val oldWidget =
        TextFieldControl[Int](5, new scalafx.scene.control.TextField())
      val container =
        ControlContainer[Int](new IntegerProperty(), oldWidget)

      val newLabelControl =
        LabelControl[Int](9, new scalafx.scene.control.Label())
      container.replaceControl(newLabelControl)

      assertEquals(container(), 9)
      newLabelControl() = 40
      assertEquals(container(), 40)
    }
  }
}
