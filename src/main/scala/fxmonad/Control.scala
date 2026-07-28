package fxmonad

import scalafx.beans.property.Property
import scalafx.beans.property.IntegerProperty
import scalafx.beans.property.StringProperty
import scala.util.Try
import scalafx.beans.property.BooleanProperty
import fxmonad.sfx._
import javafx.scene.layout.Pane
import scalafx.application.Platform
import scalafx.event.subscriptions.Subscription
import fxmonad.Conversion.castConversion
import scalafx.beans.property.DoubleProperty

object PropertyConstructor {
  given PropertyConstructor[String] = () => new StringProperty()
  given PropertyConstructor[Int] = () => new IntegerProperty()
  given PropertyConstructor[Boolean] = () => new BooleanProperty()
  given PropertyConstructor[Double] = () => new DoubleProperty()
}

@java.lang.FunctionalInterface
abstract class PropertyConstructor[A] extends Function0[Property[A, ?]]:
  def apply(): Property[A, ?]

object Conversion {
  implicit def castConversion[A, B](
      c: scala.Conversion[A, B]
  ): fxmonad.Conversion[A, B] = (input: A) => {
    Try(c(input)).toEither match {
      case Left(e)      => Left(e.getMessage())
      case Right(value) => Right(value)
    }
  }
}

// TODO: Maybe this should be one thing with an "in" and "out" method rather than having two objects everywhere. That's really annoying to keep typing, and I haven't been able to separate "in" and "out" like I thought I might; both went into ControlBase.
@java.lang.FunctionalInterface
abstract class Conversion[-T, +U] extends Function1[T, Either[String, U]]:
  self =>
  def apply(x: T): Either[String, U]

object Control {
  // TODO: I wonder if I'd prefer to define my own type of thing like Conversion but which was MyConversion[A, B] = (A) => Try[B] or (A) => Either[String, B] so I could define the error message in the converter instead of the control.

  private def selfConversion[A](): Conversion[A, A] = (x: A) => Right(x)
  given Conversion[Boolean, Boolean] = selfConversion()
  given Conversion[String, String] = selfConversion()
  given Conversion[Double, Double] = selfConversion()
  given Conversion[String, Int] =
    ((x: String) => x.toInt): scala.Conversion[String, Int]
  given Conversion[Int, String] =
    ((x: Int) => x.toString()): scala.Conversion[Int, String]
  given Conversion[Int, Double] =
    ((x: Int) => x.toDouble): scala.Conversion[Int, Double]
  given Conversion[Double, Int] =
    ((x: Double) => x.toInt): scala.Conversion[Double, Int]
  given Conversion[String, Boolean] = (
      (x: String) =>
        x match {
          case "true" => true
          case _      => false
        }
  ): scala.Conversion[String, Boolean]
  given Conversion[Int, Boolean] = (
      (x: Int) =>
        x match {
          case 0 => false
          case _ => true
        }
  ): scala.Conversion[Int, Boolean]
  given Conversion[Boolean, Int] =
    ((x: Boolean) => if (x) then 1 else 0): scala.Conversion[Boolean, Int]

  given Conversion[Boolean, String] =
    ((x: Boolean) => x.toString()): scala.Conversion[Boolean, String]

}

/** Parent class of controls that contain an naive type, such as a JavaFX
  * TextField, which is naively a String, that need translation.
  *
  * @param inConversion
  * @param outConversion
  */
abstract class ControlBase[COut, CIn](using
    inConversion: Conversion[COut, CIn],
    outConversion: Conversion[CIn, COut]
) extends Control[COut] {

  protected[fxmonad] def showError(errorMsg: String): Unit

  protected[fxmonad] def clearError(): Unit

  /** Utility method to update the property associated with this class using the
    * value of the contained control.
    *
    * @param newVal
    *   The new value that will be accessible in the default property.
    */
  protected def updateProperty(newVal: CIn) = {
    if (newVal != null) { // TODO: The else branch
      outConversion(newVal) match {
        case Right(null) =>
          showError("The control value was set to null")
        case Right(nv) =>
          clearError()
          this.update(nv)
        case Left(msg) =>
          showError(s"There was a conversion error: $msg")
      }
    }
  }
}

// TODO: Did some work to try to pass in a Monad[Control]. The first thought was to make that a way to provide access to the parent control, but that wouldn't meet the requirements because the parent control would have to be a specific Control[String] or Control[Int], and Monad[Control] would have Control#pure(A), which would produce a Control[A] and it doesn't seem right to inspect the internal type an pass in the output control in certain circumstances. Still wondering whether to provide a Monad[Control], though, and have Monad#pure produce a SimpleControl.
abstract class Control[COut] {
  protected var binder: Option[ControlBinder[COut]] = None

  // def map[B](f: (COut) => B): Control[B, ?] = new CarrierControl(f(defaultProperty()))
  def flatMap[B](f: (x: COut) => Control[B]): Control[B] = f(this.apply())

  def apply(): COut
  // TODO: Possibly make this a function instead of a value? But that would imply consistency with the other update methods, which would require Control[COut], which seems silly.
  def update(newVal: COut): Unit

  def update[B](control1: Control[B], f: B => Control[COut]): Unit

  def update[B, C](
      control1: Control[B],
      control2: Control[C],
      f: (B, C) => Control[COut]
  ): Unit

  def update[B, C, D](
      control1: Control[B],
      control2: Control[C],
      control3: Control[D],
      f: (B, C, D) => Control[COut]
  ): Unit
}

/**
  * A simple class to use to contain a value. It can't be bound right now.
  *
  * @param value
  */
class SimpleControl[COut](var value: COut) extends Control[COut] {
  override def apply(): COut = value

  override def update(newVal: COut): Unit = value = newVal

  override def update[B](control1: Control[B], f: B => Control[COut]): Unit = ???
  override def update[B, C](control1: Control[B], control2: Control[C], f: (B, C) => Control[COut]): Unit = ???
  override def update[B, C, D](control1: Control[B], control2: Control[C], control3: Control[D], f: (B, C, D) => Control[COut]): Unit = ???
}

// TODO: I can't tell if this class is tightly bound to ScalaFX, or if I'm doing it wrong.
class ControlContainer[COut, CIn, InnerControl <: scalafx.scene.control.Control](
    defaultProperty: Property[COut, ?],
    initialWrappedControl: SFXControl[COut, CIn, InnerControl]
)
(using inConversion: Conversion[COut, CIn], outConversion: Conversion[CIn, COut])
extends SFXControl[COut, CIn, InnerControl](defaultProperty, initialWrappedControl.control) {

  override def apply(): COut = defaultProperty()
  override def update(newVal: COut): Unit = defaultProperty() = newVal

  override protected[fxmonad] def showError(errorMsg: String): Unit = wrappedControl.showError(errorMsg)
  override protected[fxmonad] def clearError(): Unit = wrappedControl.clearError()

  var wrappedControl: SFXControl[COut, ?, ?] =
    scala.compiletime.uninitialized
  private var wrappedSubscription: Option[Subscription] = None

  private def setWrappedControl(control: SFXControl[COut, ?, ?]) = {
    wrappedSubscription.map(_.cancel())
    wrappedControl = control
    wrappedSubscription = Some(
      wrappedControl.defaultProperty.onChange((_, _, _) => {
        // TODO: This is broken because the ScalaFX wrapper around the JavaFX properties isn't broken-ish
        defaultProperty() = control.defaultProperty()
      })
    )
    defaultProperty() = control.defaultProperty()
  }
  setWrappedControl(initialWrappedControl)
  defaultProperty.onChange((_, _, newVal) => {
    wrappedControl.defaultProperty() = defaultProperty()
  })

  // TODO: It seems both 1. a problem and 2. necessary for the internal type to change. The only impact, really, is to use a different conversion internally and to change the signature of updateProperty. Does it need to be part of the external type declaration?
  protected[fxmonad] def replaceControl(newControl: Control[COut]) = {
    // TODO: If the new control has a different inner type than control, replace the wrapped control in the JavaFX tree with the new control and keep the passed-in control as the new wrapped control. Which seems like it'll probably be a nightmare.
    // TODO: This is a lot of the same logic as in ControlBinder#update, except for ControlBinder deferring to ControlContainer.

    def defaultBehavior() = {
      wrappedControl.defaultProperty() = newControl()
    }

    // TODO: This is at least the beginning of the checks that are needed.
    // TODO: Add checks if newControl isn't Control[COut, CIn], it won't work to replace the control.
    // TODO: Which, of course, can't be done because of type erasure
    if (
      (wrappedControl.isInstanceOf[SFXControl[?, ?, ?]]) &&
      (newControl.isInstanceOf[SFXControl[COut, ?, ?]]) &&
      (wrappedControl
        .asInstanceOf[SFXControl[?, ?, ?]]
        .control
        .getClass != newControl
        .asInstanceOf[SFXControl[COut, ?, ?]]
        .control
        .getClass()) &&
      !(newControl
        .asInstanceOf[SFXControl[?, ?, ?]]
        .control
        .isInstanceOf[SFXProxy[?]])
    ) {
      val wrappedControlSfx = wrappedControl.asInstanceOf[SFXControl[?, ?, ?]]
      val newControlSfx = newControl.asInstanceOf[SFXControl[COut, ?, ?]]
      val wrappedControlParent = wrappedControlSfx.control.parent()
      if (wrappedControlParent.isInstanceOf[Pane]) {
        val wrappedControlPane = wrappedControlParent.asInstanceOf[Pane]
        Platform.runLater {
          // TODO: This should replace the other control in the same index
          val index = wrappedControlPane
            .getChildren()
            .indexOf(wrappedControlSfx.control.delegate)
          if (index > -1) {
            wrappedControlPane
              .getChildren()
              .remove(wrappedControlSfx.control.delegate)
            wrappedControlPane
              .getChildren()
              .add(index, newControlSfx.control.delegate)
          } else { /* TODO: This probably needs something more. */ }

          // wrappedControlPane.getChildren().forEach(x => println(x.toString()))
        }
        wrappedControl = newControlSfx
      } else {
        defaultBehavior()
      }
    } else {
      defaultBehavior()
    }
  }
}
