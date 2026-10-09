package fxmonad

import scalafx.beans.property.Property
import scalafx.beans.property.IntegerProperty
import scalafx.beans.property.StringProperty
import scala.util.Try
import scalafx.beans.property.BooleanProperty
import fxmonad.sfx._
import fxmonad.Conversion.castConversion
import scalafx.beans.property.DoubleProperty
import java.util.concurrent.atomic.AtomicReference
import scala.reflect.ClassTag
import scalafx.beans.property.ObjectProperty
import scalafx.scene.paint.Color

object PropertyConstructor {
  given PropertyConstructor[String] = () => new StringProperty()
  given PropertyConstructor[Int] = () => new IntegerProperty()
  given PropertyConstructor[Boolean] = () => new BooleanProperty()
  given PropertyConstructor[Double] = () => new DoubleProperty()
  given [A]: PropertyConstructor[A] = () => new ObjectProperty[A]()
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

  trait MountContext

  /** A configurable collection used to register constructor functions to create
    * the correct subclass of [[fxmonad.Control]] based on the type being
    * exposed and control being wrapped.
    *
    * This collection is pre-configured with the combinations known to the
    * library authors, but additional configurations must be provided for
    * controls wrapping custom types. For example, for a control to expose a
    * custom case class Car in a TestField, code similar to the following is
    * necessary:
    *
    * {{{
    *    Control.lookups.getAndUpdate(lookups => {
    *      lookups + (classOf[Car] -> ({
    *        case c: javafx.scene.control.TextBox =>
    *          TextBoxControl(scalafx.scene.control.TextField(c))
    *      } :: lookups.getOrElse(classOf[Car], List())))
    *
    * }}}
    *
    * This code presumes the availability of context parameters providing a
    * [[fxmonad.Conversion[String, Car]]] and a
    * [[fxmonad.Conversion[Car, String]].
    */
  private val lookups: AtomicReference[Map[Class[?], List[
    PartialFunction[Object, Control[?]]
  ]]] = AtomicReference(
    Map(
      (classOf[String]) -> List({
        case c: javafx.scene.control.TextField =>
          ControlContainer(
            new StringProperty(),
            TextFieldControl[String](scalafx.scene.control.TextField(c))
          )
        case c: javafx.scene.control.CheckBox =>
          ControlContainer(
            new StringProperty(),
            CheckBoxControl[String](scalafx.scene.control.CheckBox(c))
          )
        case c: fxmonad.ControlPane[String] =>
          c.initializeContainer(using summon[ClassTag[String]])
          c
      }),
      (classOf[Int]) -> List({
        case c: javafx.scene.control.CheckBox =>
          ControlContainer(
            new IntegerProperty(),
            CheckBoxControl(scalafx.scene.control.CheckBox(c))
          )
        case c: javafx.scene.control.Slider =>
          ControlContainer(
            new IntegerProperty(),
            SliderControl(scalafx.scene.control.Slider(c))
          )
        case c: javafx.scene.control.TextField =>
          ControlContainer(
            new IntegerProperty(),
            TextFieldControl(scalafx.scene.control.TextField(c))
          )
        case c: fxmonad.ControlPane[Int] =>
          c.initializeContainer(using summon[ClassTag[Int]])
          c
      }),
      (classOf[Boolean]) -> List({
        case c: javafx.scene.control.CheckBox =>
          ControlContainer(
            new BooleanProperty(),
            CheckBoxControl(scalafx.scene.control.CheckBox(c))
          )
        case c: fxmonad.ControlPane[Boolean] =>
          c.initializeContainer(using summon[ClassTag[Boolean]])
          c
        case c: javafx.scene.control.RadioButton =>
          ControlContainer(
            new BooleanProperty(),
            RadioButtonControl(scalafx.scene.control.RadioButton(c))
          )
      }),
      (classOf[Double]) -> List({
        case c: javafx.scene.control.Slider =>
          ControlContainer(
            new DoubleProperty(),
            SliderControl(scalafx.scene.control.Slider(c))
          )
        case c: fxmonad.ControlPane[Double] =>
          c.initializeContainer(using summon[ClassTag[Double]])
          c
      }),
      (classOf[Color]) -> List({ case c: javafx.scene.control.ColorPicker =>
        ControlContainer(
          ColorPickerControlColor(scalafx.scene.control.ColorPicker(c))
        )
      })
    )
  )

  /** A utility function to register constructors for [[fxmonad.Control]] based
    * on the contained type and the wrapped control.
    *
    * For example: to create a [[fxmonad.Control]] that wraps a custom class
    * called Car that is specified with a JavaFX TextField (let's suppose we are
    * creating a Car whose name is specified in the TextField), you need to
    * register the following constructor:
    *
    * {{{
    *      Control.registerControl(classOf[Car],
    *        {
    *          case c: javafx.scene.control.TextField =>
    *            TextFieldControl(c)
    *        }
    *
    *      )
    * }}}
    *
    * @see
    *   [[fxmonad.ControlContainer]] for more information on whether your
    *   constructor should wrap the generated Control[A] with a
    *   [[fxmonad.ControlContainer]].
    *
    * @param typ
    *   the type of the wrapped value
    * @param constructors
    *   a PartialFunction whose cases are the possible classes that will be
    *   wrapped by a [[fxmonad.Control]] and whose return value is the
    *   [[fxmonad.Control]] that wraps it.
    */
  def registerControl[A](
      typ: Class[A],
      constructors: PartialFunction[Object, Control[A]]
  ): Unit = {
    lookups.getAndUpdate(lookups => {
      lookups +
        (typ -> (constructors :: lookups.getOrElse(typ, List())))
    }): Unit
  }

  /** A utilty function to construct a [[fxmonad.Control]] based on the
    * contained type and the wrapped control.
    *
    * For now, this is basically only useful for controls based on JavaFX
    * control classes. It's first use case was in the code generated by
    * [[fxmonad.FXMonad]], but it had to move here later in the development.
    *
    * @param typ
    *   The type contained in the [[fxmonad.Control]] monad.
    * @param control
    *   The control being wrapped by [[fxmonad.Control]].
    */
  def lookupControl[A](
      control: javafx.scene.Node
  )(using ev: ClassTag[A]): Control[A] = {
    // TODO: I wonder if there should be a special clause here checking for ControlPane rather than expecting scenarios where clients of the library have to add constructors for ControlPane to Control.lookups that are properly implemented.
    //  I had to add a block to BraceletApp that says:
    //    case c: fxmonad.ControlPane[Intensity] =>
    //      c.initializeContainer(using summon[ClassTag[Intensity]])
    //      c
    // to instantiate the pane, because Intensity is a custom class. But this would break the design pattern, which I'd rather not do; that's how libraries get unexpected behaviors.
    lookups.get
      .getOrElse(ev.runtimeClass, List())
      .reduce(_.orElse(_))
      .applyOrElse(
        control,
        c =>
          throw new Exception(
            s"Failed to find proper control for type ${ev.runtimeClass.getTypeName()} and control ${c.getClass().getTypeName()}"
          )
      )
      .asInstanceOf[Control[A]]
  }

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

/** The base for implementing subclasses. Providing updateProperty requires
  * knowing the "naive" type of the principal value of the control (String for a
  * TextField or Label, Double for a Slider, etc). There generally also needs to
  * be another [[Conversion]] object provided in the subscription to the change
  * event watching defaultProperty to convert the outward-facing type to the
  * "naive" type, but that is generally not required except in the specific
  * subclasses themselves.
  *
  * @param outConversion
  */
abstract class ControlBase[COut, CIn](using
    outConversion: Conversion[CIn, COut]
) extends Control[COut] {

  /** Utility method to update the property associated with this class using the
    * value of the contained control.
    *
    * @param newVal
    *   The new value that will be accessible in the default property.
    */
  protected def updateProperty(newVal: CIn) = {
    if (newVal != null) { // TODO: The else branch. Does it clear the value, or does it throw an exception? I don't like it clearing the value; we wouldn't know what the {0} value is for the data type.
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

/** Exposes a control (such as a JavaFX TextField or an HID device) as an
  * abstraction of the desired data type rather than the data type most "native"
  * to the control itself. For example, the most intuitive data type of a JavaFX
  * TextField is a String, but if the user is expected to type in a number, the
  * desired data type might be called PhoneNumber. Custom validation logic can
  * be inserted into the particular subclass of Control;
  * @see
  *   [[fxmonad.ControlBase]].
  *
  * The monad uses a flatMap-style (rather than map-style) so that it is
  * possible to generate an alternative control in response to dependent values.
  * For instance, a Control[String] may initially represent a Label that says
  * "This has not been initialized" but once a dependent value changes, it may
  * represent a YouTube player where the "String" in question is the URL of the
  * video to display.
  *
  * An example use:
  * {{{
  * lazy val slider1: Control[Int] = SliderControl[Int](...)
  * lazy val slider2: Control[Int] = SliderControl[Int](...)
  * lazy val outputDisplay: Control[String] = LabelControl(...)
  *
  * outputDisplay(slider1, slider2) = { (sliderVal1: Int, sliderVal2: Int) =>
  *      LabelControl((sliderVal1 + sliderVal2).toString())
  *    }
  * }}}
  *
  * @see
  *   [[fxmonad.ControlCollection]] and @see [[fxmonad.ControlPane]] for more
  *   complex uses, such as substituting new controls as a result of the bound
  *   method (which will not happen in the simple example above).
  */
trait Control[COut] {
  protected var binder: Option[ControlBinder[COut]] = None

  val defaultProperty: Property[COut, ?]

  protected[fxmonad] def updateFrom: PartialFunction[Control[COut], Unit] = {
    case source => defaultProperty() = source.defaultProperty()
  }

  /** Displays the error message. This is not intended to be called outside the
    * fxmonad library; it is exposed as public only so that it can be
    * implemented in client subclasses as well as called from other classes in
    * this package.
    *
    * @param errorMsg
    *   The message to display to the user to indicate a validation failure.
    */
  def showError(errorMsg: String): Unit

  /** Removes the error message. This is not intended to be called outside the
    * fxmonad library; it is exposed as public only so that it can be
    * implemented in client subclasses as well as called from other classes in
    * this package.
    */
  def clearError(): Unit

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

  def mountControl(context: Option[Control.MountContext]): Unit
  def unmountControl(): Option[Control.MountContext]

  def isProxy = false
}
