package fxmonad.sfx

import fxmonad.Control
import fxmonad.Control.given
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javafx.scene.control.TextField
import org.scalacheck.Gen
import org.scalacheck.Prop
import org.scalacheck.Test
import org.testfx.api.FxToolkit
import scalafx.application.Platform

class TextFieldControlBindingSpec extends munit.FunSuite {

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
    "lookup-created integer TextField validates values before updating dependents"
  ) {
    val (inputWidget, outputWidget, input, output) = onFx {
      val inputWidget = new TextField("0")
      val outputWidget = new TextField()
      val input = Control
        .lookupControl(classOf[Int], inputWidget)
        .asInstanceOf[Control[Int]]
      val output = Control
        .lookupControl(classOf[String], outputWidget)
        .asInstanceOf[Control[String]]

      output(input) = { value => TextFieldControl(s"value=$value") }

      (inputWidget, outputWidget, input, output)
    }

    var retainedValue = 0
    val parameters = Test.Parameters.default.withMinSuccessfulTests(100)
    val validValues = Prop.forAll(Gen.choose(-100000, 100000)) { value =>
      onFx {
        inputWidget.setText(value.toString)
      }
      val isUpdated = onFx {
        input() == value && output() == s"value=$value" &&
        outputWidget.getText() == s"value=$value"
      }
      if (isUpdated) retainedValue = value
      isUpdated
    }
    val validResult = Test.check(parameters, validValues)
    assert(validResult.passed, validResult.toString)

    val invalidValues = Prop.forAll(Gen.alphaStr) { suffix =>
      onFx {
        inputWidget.setText("invalid:" + suffix)
      }
      onFx {
        inputWidget.getText() == "invalid:" + suffix &&
        input() == retainedValue &&
        output() == s"value=$retainedValue" &&
        outputWidget.getText() == s"value=$retainedValue"
      }
    }
    val invalidResult = Test.check(parameters, invalidValues)
    assert(invalidResult.passed, invalidResult.toString)
  }
}
