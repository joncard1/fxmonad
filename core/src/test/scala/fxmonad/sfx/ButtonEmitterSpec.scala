package fxmonad.sfx

import fxmonad.EventProcessor
import fxmonad.SimpleEventProcessor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javafx.scene.control.Button
import org.testfx.api.FxToolkit
import scalafx.application.Platform

class ButtonEmitterSpec extends munit.FunSuite {

  override def beforeEach(context: BeforeEach): Unit = {
    FxToolkit.registerPrimaryStage(): Unit
  }

  override def afterEach(context: AfterEach): Unit = {
    FxToolkit.cleanupStages()
  }

  private def onFx[A](body: => A): A = {
    val latch = new CountDownLatch(1)
    var result: Option[A] = None
    var error: Option[Throwable] = None
    Platform.runLater(() => {
      try { result = Some(body) }
      catch { case t: Throwable => error = Some(t) }
      finally { latch.countDown() }
    })
    assert(latch.await(30, TimeUnit.SECONDS), "FX thread callback never ran")
    error.foreach(throw _)
    result.get
  }

  test(
    "ButtonEmitter builds an action message and dispatches it to the handler"
  ) {
    val button = onFx { new Button() }
    val processor = new SimpleEventProcessor[String]
    val handled = new CountDownLatch(1)
    var message = ""
    var handlerRanOnFxThread = false
    processor.registerHandler { case received =>
      message = received
      handlerRanOnFxThread = Platform.isFxApplicationThread
      handled.countDown()
    }

    given EventProcessor[String] = processor
    new ButtonEmitter[String](button).onAction { event =>
      if (event.getSource eq button) "button-action" else "unexpected-source"
    }
    processor.seal()

    onFx { button.fire() }
    assert(handled.await(5, TimeUnit.SECONDS), "button message was not handled")
    assertEquals(message, "button-action")
    assert(handlerRanOnFxThread, "event handler did not run on the FX thread")
  }
}
