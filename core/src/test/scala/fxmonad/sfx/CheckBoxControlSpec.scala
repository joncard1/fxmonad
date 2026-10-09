package fxmonad.sfx

import fxmonad.Control
import fxmonad.Control.given
import fxmonad.ControlContainer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javafx.scene.control.{CheckBox, Label, TextField}
import org.scalacheck.Gen
import org.scalacheck.Prop
import org.scalacheck.Test
import org.testfx.api.FxToolkit
import scalafx.application.Platform
import scalafx.beans.property.StringProperty

class CheckBoxControlSpec extends munit.FunSuite {

  private val parameters = Test.Parameters.default.withMinSuccessfulTests(40)
  private val selectedValues = Gen.oneOf(true, false)

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

  private def checkSelectedValues(predicate: Boolean => Boolean): Unit = {
    val result = Test.check(parameters, Prop.forAll(selectedValues)(predicate))
    assert(result.passed, result.toString)
  }

  test("lookup-created CheckBox[Boolean] updates its dependent control") {
    val (widget, outputWidget, input, output) = onFx {
      val widget = new CheckBox()
      val outputWidget = new TextField()
      val input = Control
        .lookupControl(classOf[Boolean], widget)
        .asInstanceOf[Control[Boolean]]
      val output = Control
        .lookupControl(classOf[String], outputWidget)
        .asInstanceOf[Control[String]]
      output(input) = { selected => TextFieldControl(s"selected=$selected") }
      (widget, outputWidget, input, output)
    }

    checkSelectedValues { selected =>
      onFx { widget.setSelected(selected) }
      onFx {
        input() == selected && output() == s"selected=$selected" &&
        outputWidget.getText() == s"selected=$selected"
      }
    }
  }

  test("lookup-created CheckBox[Int] maps selection to zero or one") {
    val (widget, outputWidget, input, output) = onFx {
      val widget = new CheckBox()
      val outputWidget = new TextField()
      val input = Control
        .lookupControl(classOf[Int], widget)
        .asInstanceOf[Control[Int]]
      val output = Control
        .lookupControl(classOf[String], outputWidget)
        .asInstanceOf[Control[String]]
      output(input) = { value => TextFieldControl(s"count=$value") }
      (widget, outputWidget, input, output)
    }

    checkSelectedValues { selected =>
      val expected = if (selected) 1 else 0
      onFx { widget.setSelected(selected) }
      onFx {
        input() == expected && output() == s"count=$expected" &&
        outputWidget.getText() == s"count=$expected"
      }
    }
  }

  test("lookup-created CheckBox[String] maps selection to true or false") {
    val (widget, outputWidget, input, output) = onFx {
      val widget = new CheckBox()
      val outputWidget = new TextField()
      val input = Control
        .lookupControl(classOf[String], widget)
        .asInstanceOf[Control[String]]
      val output = Control
        .lookupControl(classOf[String], outputWidget)
        .asInstanceOf[Control[String]]
      output(input) = { value => TextFieldControl(s"state=$value") }
      (widget, outputWidget, input, output)
    }

    checkSelectedValues { selected =>
      val expected = selected.toString
      onFx { widget.setSelected(selected) }
      onFx {
        input() == expected && output() == s"state=$expected" &&
        outputWidget.getText() == s"state=$expected"
      }
    }
  }

  test("lookup-created CheckBox can drive a read-only Label") {
    val (widget, labelWidget) = onFx {
      val widget = new CheckBox()
      val labelWidget = new Label()
      val input = Control
        .lookupControl(classOf[Boolean], widget)
        .asInstanceOf[Control[Boolean]]
      val output = ControlContainer[String](
        new StringProperty(),
        LabelControl[String](new scalafx.scene.control.Label(labelWidget))
      )
      output(input) = { selected => LabelControl(s"label=$selected") }
      (widget, labelWidget)
    }

    checkSelectedValues { selected =>
      onFx { widget.setSelected(selected) }
      onFx { labelWidget.getText() == s"label=$selected" }
    }
  }
}
