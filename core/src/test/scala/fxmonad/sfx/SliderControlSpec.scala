package fxmonad.sfx

import fxmonad.Control
import fxmonad.Control.given
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javafx.scene.control.{Slider, TextField}
import org.scalacheck.Gen
import org.scalacheck.Prop
import org.scalacheck.Test
import org.testfx.api.FxToolkit
import scalafx.application.Platform

class SliderControlSpec extends munit.FunSuite {

  private val parameters = Test.Parameters.default.withMinSuccessfulTests(40)

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

  private def checkProperty[A](
      generator: Gen[A]
  )(predicate: A => Boolean): Unit = {
    val result = Test.check(parameters, Prop.forAll(generator)(predicate))
    assert(result.passed, result.toString)
  }

  test("lookup-created Slider[Double] updates its dependent control") {
    val (widget, outputWidget, input, output) = onFx {
      val widget = new Slider(0.0, 100.0, 0.0)
      val outputWidget = new TextField()
      val input = Control
        .lookupControl[Double](widget)
        .asInstanceOf[Control[Double]]
      val output = Control
        .lookupControl[String](outputWidget)
        .asInstanceOf[Control[String]]
      output(input) = { value => TextFieldControl(s"slider=$value") }
      (widget, outputWidget, input, output)
    }

    checkProperty(Gen.choose(0.0, 100.0)) { value =>
      onFx { widget.setValue(value) }
      onFx {
        input() == value && output() == s"slider=$value" &&
        outputWidget.getText() == s"slider=$value"
      }
    }
  }

  test(
    "lookup-created Slider[Int] truncates values and updates its dependent"
  ) {
    val (widget, outputWidget, input, output) = onFx {
      val widget = new Slider(0.0, 100.0, 0.0)
      val outputWidget = new TextField()
      val input = Control
        .lookupControl[Int](widget)
        .asInstanceOf[Control[Int]]
      val output = Control
        .lookupControl[String](outputWidget)
        .asInstanceOf[Control[String]]
      output(input) = { value => TextFieldControl(s"slider=$value") }
      (widget, outputWidget, input, output)
    }

    checkProperty(Gen.choose(0, 100)) { value =>
      onFx { widget.setValue(value.toDouble + 0.75) }
      onFx {
        input() == value && output() == s"slider=$value" &&
        outputWidget.getText() == s"slider=$value"
      }
    }
  }
}
