package fxmonad

import scalafx.application.Platform
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.testfx.api.FxToolkit

class SimpleEventProcessorSpec extends munit.FunSuite {

  sealed trait TestMsg
  case object A extends TestMsg
  case object B extends TestMsg

  override def beforeEach(context: BeforeEach): Unit = {
    FxToolkit.registerPrimaryStage() : Unit
  }
  override def afterEach(context: AfterEach): Unit = {
    FxToolkit.cleanupStages()
  }

  test("dispatch runs the first matching handler on the FX thread") {
    val latch = new CountDownLatch(1)
    var result = ""
    var onFxThread = false

    val ep = new SimpleEventProcessor[TestMsg]
    ep.registerHandler { case A =>
      result = "first"
      onFxThread = Platform.isFxApplicationThread
      latch.countDown()
    }
    // A second handler that also matches A must be shadowed by the first.
    ep.registerHandler { case A =>
      result = "second"
    }
    ep.seal()
    ep.dispatch(A)

    assert(latch.await(5, TimeUnit.SECONDS), "handler was never invoked")
    assertEquals(result, "first")
    assert(onFxThread, "handler did not run on the FX thread")
  }

  test("registration order determines which handler wins") {
    val latch = new CountDownLatch(1)
    var result = ""

    val ep = new SimpleEventProcessor[TestMsg]
    ep.registerHandler { case B => result = "handles-b" }
    ep.registerHandler {
      case A => result = "handles-a"
      case B => result = "also-b"
    }
    ep.seal()
    ep.dispatch(B)

    // Drain the FX queue so the assertion runs after dispatch completes.
    Platform.runLater(() => latch.countDown())
    assert(latch.await(5, TimeUnit.SECONDS))
    assertEquals(result, "handles-b")
  }

  test("an unmatched message does not throw") {
    val latch = new CountDownLatch(1)

    val ep = new SimpleEventProcessor[TestMsg]
    ep.registerHandler { case A => () }
    ep.seal()
    ep.dispatch(B) // no handler for B -> dead letter, must not fail

    Platform.runLater(() => latch.countDown())
    assert(latch.await(5, TimeUnit.SECONDS))
  }

  test("seal is idempotent") {
    val ep = new SimpleEventProcessor[TestMsg]
    ep.registerHandler { case A => () }
    ep.seal()
    ep.seal()
  }

  test("registering after seal throws") {
    val ep = new SimpleEventProcessor[TestMsg]
    ep.seal()
    intercept[IllegalStateException] {
      ep.registerHandler { case A => () }
    }
  }

  test("dispatching before seal throws") {
    val ep = new SimpleEventProcessor[TestMsg]
    ep.registerHandler { case A => () }
    intercept[IllegalStateException] {
      ep.dispatch(A)
    }
  }

  test("Using.resource seals the processor when the block closes") {
    val latch = new CountDownLatch(1)
    var handled = false

    val ep = new SimpleEventProcessor[TestMsg]
    scala.util.Using.resource(ep: EventProcessor[TestMsg]) { p =>
      p.registerHandler { case A =>
        handled = true
        latch.countDown()
      }
    }
    // After the block, the processor is sealed, so dispatch is legal and a
    // second registration is not.
    intercept[IllegalStateException] {
      ep.registerHandler { case B => () }
    }
    ep.dispatch(A)
    assert(latch.await(5, TimeUnit.SECONDS))
    assert(handled)
  }
}
