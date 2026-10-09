package fxmonad.sfx

import fxmonad.Control
import fxmonad.ControlContainer
import fxmonad.Conversion
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javafx.scene.control.{RadioButton, TextField}
import javafx.scene.paint.Color as JFXColor
import org.scalacheck.Gen
import org.scalacheck.Prop
import org.scalacheck.Test
import org.testfx.api.FxToolkit
import scalafx.application.Platform
import scalafx.beans.property.StringProperty
import scalafx.scene.paint.Color

class OtherControlBindingSpec extends munit.FunSuite {

  private val parameters = Test.Parameters.default.withMinSuccessfulTests(40)

  private given Conversion[String, Object] = value => Right(value)
  private given Conversion[Object, String] = {
    case value: String => Right(value)
    case _             => Left("toggle user data must be a String")
  }

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

  test("lookup-created RadioButton updates its dependent control") {
    val (widget, outputWidget, input, output) = onFx {
      val widget = new RadioButton()
      val outputWidget = new TextField()
      val input = Control
        .lookupControl(classOf[Boolean], widget)
        .asInstanceOf[Control[Boolean]]
      val output = Control
        .lookupControl(classOf[String], outputWidget)
        .asInstanceOf[Control[String]]
      output(input) = { selected => TextFieldControl(s"radio=$selected") }
      (widget, outputWidget, input, output)
    }

    checkProperty(Gen.oneOf(true, false)) { selected =>
      onFx { widget.setSelected(selected) }
      onFx {
        input() == selected && output() == s"radio=$selected" &&
        outputWidget.getText() == s"radio=$selected"
      }
    }
  }

  test("lookup-created ColorPicker updates its dependent control") {
    val (widget, outputWidget, input, output) = onFx {
      val widget = new javafx.scene.control.ColorPicker(JFXColor.WHITE)
      val outputWidget = new TextField()
      val input = Control
        .lookupControl(classOf[Color], widget)
        .asInstanceOf[Control[Color]]
      val output = Control
        .lookupControl(classOf[String], outputWidget)
        .asInstanceOf[Control[String]]
      output(input) = { value => TextFieldControl(s"color=${value.toString}") }
      (widget, outputWidget, input, output)
    }

    val colors = for {
      red <- Gen.choose(0, 255)
      green <- Gen.choose(0, 255)
      blue <- Gen.choose(0, 255)
    } yield (red, green, blue)

    checkProperty(colors) { case (red, green, blue) =>
      onFx { widget.setValue(JFXColor.rgb(red, green, blue)) }
      onFx {
        val colorText = input().toString
        input().delegate == widget.getValue &&
        output() == s"color=$colorText" &&
        outputWidget.getText() == s"color=$colorText"
      }
    }
  }

  test(
    "ToggleGroup selection updates dependents and rejects invalid user data"
  ) {
    val (online, bracelet, invalid, input, output, outputWidget) = onFx {
      val group = new javafx.scene.control.ToggleGroup()
      val online = new RadioButton()
      online.setUserData("onscreen")
      online.setToggleGroup(group)
      val bracelet = new RadioButton()
      bracelet.setUserData("bracelet")
      bracelet.setToggleGroup(group)
      val invalid = new RadioButton()
      invalid.setUserData(Int.box(1))
      invalid.setToggleGroup(group)
      online.setSelected(true)

      val groupControl = new ToggleGroupControl[String](
        new StringProperty(),
        new scalafx.scene.control.ToggleGroup(group)
      )
      val input = ControlContainer[String](new StringProperty(), groupControl)
      val outputWidget = new TextField()
      val output = Control
        .lookupControl(classOf[String], outputWidget)
        .asInstanceOf[Control[String]]
      output(input) = { mode => TextFieldControl(s"mode=$mode") }
      (online, bracelet, invalid, input, output, outputWidget)
    }

    val modes = Gen.oneOf("onscreen", "bracelet")
    checkProperty(modes) { mode =>
      onFx {
        if (mode == "onscreen") online.setSelected(true)
        else bracelet.setSelected(true)
      }
      onFx {
        input() == mode && output() == s"mode=$mode" &&
        outputWidget.getText() == s"mode=$mode"
      }
    }

    val retainedMode = onFx { input() }
    onFx { invalid.setSelected(true) }
    onFx {
      input() == retainedMode && output() == s"mode=$retainedMode" &&
      outputWidget.getText() == s"mode=$retainedMode"
    }
  }
}
