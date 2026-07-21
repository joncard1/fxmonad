package jackflashtech.fxmonad

import scalafx.scene.{control => sfxc}
import javafx.event.EventDispatchChain
import javafx.geometry.Point2D
import javafx.scene.AccessibleAction
import javafx.scene.Node
import javafx.collections.ObservableList
import scalafx.beans.property.DoubleProperty
import scalafx.beans.value.ObservableValue
import scalafx.beans.property.StringProperty
import sfxc.TextField
import scalafx.collections.ObservableBuffer.Add
import scalafx.collections.ObservableBuffer
import scalafx.collections.ObservableBuffer.Remove
import scalafx.collections.ObservableBuffer.Reorder
import scalafx.collections.ObservableBuffer.Update
import sfxc.CheckBox
import sfxc.Slider
import sfxc.Label
import sfxc.ColorPicker
import scalafx.scene.paint.Color

case class Change(propertyName: String, oldVal: Any, newVal: Any)

// TODO: This is really for intercepting calls to properties of a control, creating a list of changes to apply to a real control so that those changes can be applied to a real control later. Mostly, calls to normal controls should not be supported.
// TODO: It seems like there should be a programmtic way to build this, like with macros, to just override every definition except the ones that are explicitly defined.
// TODO: I wonder if I should really create a JFX Control proxy instead of an ScalaFX control proxy. It seems like it'll be harder to intercept things going to
/** This trait converts a ScalaFX control to a proxy of a ScalaFX control that
  * logs the changes made to the control for the purpose of replaying those
  * changes to an actual control. This allows the use case where a logic method
  * creates a new control and alters the properties of the control to change the
  * control's behavior or display characteristics. For instance:
  *
  * {{{
  * textBoxC3(textBoxC, textBoxC2) = {
  *   (shouldBeInt: Int, shouldBeString: String) =>
  *     val newC = TextFieldControlString()
  *       if (shouldBeInt > 10) {
  *         newC.control.styleClass.add("emphasis")
  *       } else {
  *         newC.control.styleClass.removeAll("emphasis")
  *       }
  *     newC.defaultProperty() =
  *       s"${shouldBeInt.toString()} and ${shouldBeString}"
  *     newC
  * }
  * }}}
  *
  * In this example, if one of the input values is big enough, the style of the
  * output control is changed. The default constructor of TextFieldControlString
  * uses a SFXProxy, so changes to the underlying control will be replayed on
  * the control already existent in the application in the object textBoxC3.
  * This allows the settings already in textBoxC3.control to continue, and for
  * the user to change those properties, or potentially change the control type
  * itself.
  *
  * An alternative implementation that this may change to is to look like
  * "TextFieldControlString("someid")" and have the system look up the control
  * with id "someid" and create the Control[String] with that control
  * pre-populated, rather than using a proxy. That may be needed, considering
  * the problem with removing styleClass entries (for example).
  */
sealed trait SFXProxy[A <: sfxc.Control] { this: A =>
  protected[fxmonad] var changes: List[Change] = List()

  // TODO: This is a bad error message. It's ok for devs using the library, not for showing users of the dev's project.
  /**
    * A helper method for methods that should not be called from the FXMonad system.
    *
    * @return
    */
  private def throwError = throw new Exception(
    "This is not a real control. It is used as a proxy in the fxmonad system."
  )

  protected def applyChangesPF(control: A): PartialFunction[Change, Unit] = {
    case c @ Change("prefHeight", _, _) =>
      control.prefHeight.set(c.newVal.asInstanceOf[Double])
    case c @ Change("style", _, _) =>
      control.style.set(c.newVal.asInstanceOf[String])
    case c @ Change("styleClass", _, _) =>
      c.newVal.asInstanceOf[ObservableBuffer.Change[String]] match {
        case Add(position, added) =>
          control.styleClass.insertAll(position, added)
        case Remove(position, removed) =>
          control.styleClass.remove(position, removed.size)
        case Reorder(start, end, permutation) => ???
        case Update(from, to)                 => ???
      }
  }

  def applyChanges(control: A): Unit = {
    var revChanges = changes.reverse
    for {
      change <-
        revChanges // TODO: This is super dicey: I'm relying on the name being correct to match the types, which isn't very stable and I don't really know how to do what I want better.
      _ <- List(applyChangesPF(control)(change))
    } yield (())
  }

  prefHeight.onChange((prop, oldVal, newVal) => {
    changes = Change(
      "prefHeight",
      oldVal.doubleValue(),
      newVal.doubleValue()
    ) :: changes
  })

  style.onChange((prop, oldVal, newVal) => {
    changes = Change("style", oldVal, newVal) :: changes
  })

  styleClass.onChange((prop, localChanges) => {
    changes = localChanges
      .map(x => Change("styleClass", null, x))
      .reverse
      .toList ::: changes
  })

  /**
    * Calling this is not supported from the FXMonad system.
    *
    * @param tail
    * @return
    */
  override def buildEventDispatchChain(
      tail: scalafx.event.EventDispatchChain
  ): scalafx.event.EventDispatchChain = throwError
  /**
    * Calling this is not supported from the FXMonad system.
    */
  override def autosize(): Unit = throwError
  // TODO: This is too boring and I'm moving on to stuff I want to do.
}

class TextFieldProxy extends TextField with SFXProxy[TextField] {

  text.onChange((prop, oldVal, newVal) => {
    changes = Change("text", oldVal, newVal) :: changes
  })

  override protected def applyChangesPF(
      control: TextField
  ): PartialFunction[Change, Unit] = {
    val localChange: PartialFunction[Change, Unit] = {
      case c @ Change("text", _, _) =>
        control.text() = c.newVal.asInstanceOf[String]
    }
    localChange.orElse(super.applyChangesPF(control))
  }
}

class CheckBoxProxy extends CheckBox with SFXProxy[CheckBox] {
  selected.onChange((prop, oldVal, newVal) => {
    changes = Change("selected", oldVal, newVal) :: changes
  })

  override protected def applyChangesPF(
      control: sfxc.CheckBox
  ): PartialFunction[Change, Unit] = {
    val localChange: PartialFunction[Change, Unit] = {
      case c @ Change("selected", _, _) =>
        control.selected() = c.newVal.asInstanceOf[Boolean]
    }
    localChange.orElse(super.applyChangesPF(control))
  }
}

class SliderProxy extends Slider with SFXProxy[Slider] {
    value.onChange((prop, oldVal, newVal) => {
      changes = Change("value", oldVal, newVal) :: changes
    })

    override protected def applyChangesPF(control: sfxc.Slider): PartialFunction[Change, Unit] = {
      val localChange: PartialFunction[Change, Unit] = {
        case c @ Change("value", _, _) => 
          control.value() = c.newVal.asInstanceOf[Double]
      }
      localChange.orElse(super.applyChangesPF(control))
    }
}

class LabelProxy extends Label with SFXProxy[Label] {
  text.onChange((prox, oldVal, newVal) => {
    changes = Change("text", oldVal, newVal) :: changes
  })

  override protected def applyChangesPF(control: sfxc.Label): PartialFunction[Change, Unit] = {
    val localChange: PartialFunction[Change, Unit] = {
      case c @ Change("text", _, _) =>
        control.text() = c.newVal.asInstanceOf[String]
    }
    localChange.orElse(super.applyChangesPF(control))
  }
}

class ColorPickerProxy extends sfxc.ColorPicker with SFXProxy[ColorPicker] {
  this.value.onChange((prox, oldVal, newVal) => {
    changes = Change("value", oldVal, newVal) :: changes
  })

  override protected def applyChangesPF(control: ColorPicker): PartialFunction[Change, Unit] = {
    val localChange: PartialFunction[Change, Unit] = {
      case c @ Change("value", _, _) =>
        control.value() = c.newVal.asInstanceOf[Color]
    }
    localChange.orElse(super.applyChangesPF(control))
  }
}