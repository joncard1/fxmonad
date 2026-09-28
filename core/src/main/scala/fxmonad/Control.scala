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
import scalafx.scene.control.{
  TextField,
  CheckBox,
  Slider,
  Label,
  ColorPicker,
  RadioButton
}

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

abstract class ControlBase[COut, CIn](using
    inConversion: Conversion[COut, CIn],
    outConversion: Conversion[CIn, COut]
) extends Control[COut] {

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
          defaultProperty() = nv
        case Left(msg) =>
          showError(s"There was a conversion error: $msg")
      }
    }
  }
}

abstract class Control[COut] {
  protected var binder: Option[ControlBinder[COut]] = None

  val defaultProperty: Property[COut, ?]

  /** Only fxmonad-internal code (conversion/binding machinery) may trigger
    * error display; the actual rendering is left to `displayError`, which any
    * subclass - in any package - can implement.
    */
  protected[fxmonad] def showError(errorMsg: String): Unit = displayError(
    errorMsg
  )
  protected[fxmonad] def clearError(): Unit = clearDisplayedError()

  protected def displayError(errorMsg: String): Unit
  protected def clearDisplayedError(): Unit

  // def map[B](f: (COut) => B): Control[B, ?] = new CarrierControl(f(defaultProperty()))
  def flatMap[B](f: (x: COut) => Control[B]): Control[B] = f(defaultProperty())

  def apply(): COut = defaultProperty()
  def update(newVal: COut) = defaultProperty() = newVal

  def update[B](control1: Control[B], f: B => Control[COut]): Unit = {
    binder = {
      binder match {
        case None       =>
        case Some(bind) => bind.dispose()
      }
      Some(new ControlBinder1(control1, this, f))
    }
    binder.flatMap(x => Option(x.updateValue())).get
  }

  def update[B, C](
      control1: Control[B],
      control2: Control[C],
      f: (B, C) => Control[COut]
  ): Unit = {
    binder = {
      binder match {
        case None       =>
        case Some(bind) => bind.dispose()
      }
      Some(new ControlBinder2(control1, control2, this, f))
    }
    binder.flatMap(x => Option(x.updateValue())).get
  }

  def update[B, C, D](
      control1: Control[B],
      control2: Control[C],
      control3: Control[D],
      f: (B, C, D) => Control[COut]
  ): Unit = {
    binder = {
      binder match {
        case None       =>
        case Some(bind) => bind.dispose()
      }
      Some(new ControlBinder3(control1, control2, control3, this, f))
    }
    binder.flatMap(x => Option(x.updateValue())).get
  }
}

// TODO: I think the idea here is to someday create the proxy for javafx.scene.control.Control
//  (at least) that can have values set on it and then a diff can be run so that
//  the changed variables get transferred to the underlying object (if it's the
//  same type) or possibly the control is replaced (if it's a different type) in
//  the JavaFX render tree. Then, change the signature of the "update" methods so
//  that they are more like flatMap functions instead of map functions, and pass
//  in a Monad[Control] that can be used to create an instance of Control[A]. That
//  way, the logic function can potentially change the properties of the display
//  controls as part of the logic. I have no idea how gradio does it, how they
//  return a complete object and then only transfer the diff to the old object, or
//  make the new object a clone of the old one somehow before allowing them the
//  change the properties, or what.
// This would require more sensible creation logic for Control[A] than I currently
//  have, which has separate classes for the entire cross-product of supported
//  values and controls.
// How would the system create the correct instance of Monad[Control] for the type of control the user will want to create? So that Monad[Control]#pure() will create an instance of Control[A] with the correct containing control? Or maybe provide Control.create(value, control) and have the lookup performed there? In some extensible way that wouldn't break if it's run at compile time (because of FXMonad)?
// I could change the signature of update to be (..., f: (Control[A], Control[B], Control[C]) =>? (Monad[Control]) => Control[D]), but that would enforce that the created Control would create Control[D]. And could I pass that the ControlBinder as f(_, _, _)(using aMonad)? Or would I just change ControlBinder?
// Is this useful to create Monad[Control] if flatMap is built-in and the monad couldn't be summoned because pure is all strange?
/*
class CarrierControl[A](value: A)(using inConversion: Conversion[COut, CIn], outConversion: Conversion[CIn, COut]) extends Control[A, ?](using inConversion, outConversion) {

    override val defaultProperty: Property[A, ?] = ???

    override def update[B](control1: Control[B, ?], f: B => A): Control[A, ?] = ???
    override def update[B, C](control1: Control[B, ?], control2: Control[C, ?], f: (B, C) => A): Control[A, ?] = ???
    override def update[B, C, D](control1: Control[B, ?], control2: Control[C, ?], control3: Control[D, ?], f: (B, C, D) => A): Control[A, ?] = ???
}
 */
// TODO: I think the purpose of this one is to give the system a chance to replace the control internally
// TODO: I wonder if CIn on this object should be the COut of the contained control?
// TODO: I wonder if the types can be like Control[COut] -> ControlContainer[COut], ControlBase[COut, CIn] (which contains updateProperty) -> SFXControl -> all the others
class ControlContainer[COut](
    override val defaultProperty: Property[COut, ?],
    val control: Control[COut]
) extends Control[COut] {

  private var wrappedControl: Control[COut] = scala.compiletime.uninitialized
  private var wrappedSubscription: Option[Subscription] = None

  private def setWrappedControl(control: Control[COut]) = {
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
  setWrappedControl(control)
  defaultProperty.onChange((_, _, newVal) => {
    wrappedControl.defaultProperty() = defaultProperty()
  })

  override protected[fxmonad] def showError(errorMsg: String): Unit = {
    wrappedControl.showError(errorMsg)
  }

  override protected[fxmonad] def clearError(): Unit = {
    wrappedControl.clearError()
  }

  // displayError/clearDisplayedError are unused here: ControlContainer has no
  // UI of its own, so it overrides showError/clearError directly to delegate
  // to the wrapped control instead.
  override protected def displayError(errorMsg: String): Unit = ()
  override protected def clearDisplayedError(): Unit = ()

  /** Reconciles the currently wrapped control against a newly-produced one
    * (from a binding function passed to `update`), in one of a few ways:
    *   - `newControl` has no widget of its own (e.g. it's another
    *     `ControlContainer`, or any other non-`SFXControl`): whatever widget is
    *     currently on screen is removed, and only the value is tracked from
    *     then on.
    *   - `wrappedControl` has no widget but `newControl` does: there's no way
    *     to know where in the JavaFX tree the new widget should go. Left
    *     unimplemented for now; the value is still tracked live.
    *   - Both are widget-backed: if `newControl` is a proxy of the same widget
    *     class, its recorded property changes are replayed onto the live widget
    *     in place. If it's a proxy of a *different* widget class, there's no
    *     way to attach a proxy node into the live scene graph either, so only
    *     the value is tracked. Otherwise, the live widget is swapped out for
    *     the new one in the JavaFX tree.
    */
  protected[fxmonad] def replaceControl(newControl: Control[COut]): Unit = {
    def trackValueOnly(): Unit = {
      wrappedControl.defaultProperty() = newControl.defaultProperty()
    }

    def removeWrappedWidgetFromParent(): Unit = {
      wrappedControl match {
        case sfx: SFXControl[?, ?, ?] =>
          sfx.control.parent() match {
            case pane: Pane =>
              Platform.runLater {
                pane.getChildren().remove(sfx.control.delegate)
              }
            case _ => ()
          }
        case _ => ()
      }
    }

    def swapWidget(
        oldWidget: scalafx.scene.control.Control,
        newSfx: SFXControl[COut, ?, ?]
    ): Unit = {
      oldWidget.parent() match {
        case pane: Pane =>
          Platform.runLater {
            val index = pane.getChildren().indexOf(oldWidget.delegate)
            if (index > -1) {
              pane.getChildren().remove(oldWidget.delegate)
              pane.getChildren().add(index, newSfx.control.delegate)
            }
          }
        case _ => () // Not attached to a Pane; nothing to move in the scene.
      }
      setWrappedControl(newSfx)
    }

    if (!newControl.isInstanceOf[SFXControl[?, ?, ?]]) {
      removeWrappedWidgetFromParent()
      setWrappedControl(newControl)
    } else if (!wrappedControl.isInstanceOf[SFXControl[?, ?, ?]]) {
      // TODO: no widget to anchor the placement decision on; not implemented.
      setWrappedControl(newControl)
    } else {
      val oldWidget = wrappedControl.asInstanceOf[SFXControl[?, ?, ?]].control
      val newSfx = newControl.asInstanceOf[SFXControl[COut, ?, ?]]
      (oldWidget, newSfx.control) match {
        case (c1: TextField, c2: TextFieldProxy)     => c2.applyChanges(c1)
        case (c1: CheckBox, c2: CheckBoxProxy)       => c2.applyChanges(c1)
        case (c1: Slider, c2: SliderProxy)           => c2.applyChanges(c1)
        case (c1: Label, c2: LabelProxy)             => c2.applyChanges(c1)
        case (c1: ColorPicker, c2: ColorPickerProxy) => c2.applyChanges(c1)
        case (c1: RadioButton, c2: RadioButtonProxy) => c2.applyChanges(c1)
        case (_, proxy) if proxy.isInstanceOf[SFXProxy[?]] =>
          // A proxy of a different widget class than the live one: can't
          // replay (wrong shape) and can't attach a proxy node into the live
          // scene graph either. Track the value only.
          trackValueOnly()
        case _ =>
          swapWidget(oldWidget, newSfx)
      }
    }
  }
}
