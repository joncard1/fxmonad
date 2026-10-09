package fxmonad.sfx

import scalafx.scene.{control => sfxc}
import scalafx.beans.property.DoubleProperty
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
import sfxc.RadioButton
import scalafx.scene.paint.Color
import sfxc.ToggleGroup
import sfxc.Toggle
import scalafx.application.Platform

case class Change(propertyName: String, oldVal: Any, newVal: Any)

trait Proxy[A] {
  protected[fxmonad] var changes: List[Change] = List()

  def applyChanges(control: A): Unit
}

// TODO: It seems like there should be a programmtic way to build this, like with macros, to just override every definition except the ones that are explicitly defined.
// TODO: I wonder if I should really create a JFX Control proxy instead of an ScalaFX control proxy.
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
sealed trait SFXProxy[A <: sfxc.Control] extends Proxy[A] { this: A =>

  // TODO: This is a bad error message. It's ok for devs using the library, not for showing users of the dev's project.
  /** A helper method for methods that should not be called from the FXMonad
    * system.
    *
    * @return
    */
  private def throwError = throw new Exception(
    "This is not a real control. It is used as a proxy in the fxmonad system."
  )

  protected def applyChangesPF(control: A): PartialFunction[Change, Unit] = {
    case c @ Change("prefHeight", _, _) =>
      Platform.runLater {
        control.prefHeight.set(c.newVal.asInstanceOf[Double])
      }
    case c @ Change("style", _, _) =>
      Platform.runLater {
        control.style.set(c.newVal.asInstanceOf[String])
      }
    case c @ Change("prefWidth", _, _) =>
      Platform.runLater {
        control.prefWidth.set(c.newVal.asInstanceOf[Number].doubleValue())
      }
    case c @ Change("minWidth", _, _) =>
      Platform.runLater {
        control.minWidth.set(c.newVal.asInstanceOf[Number].doubleValue())
      }
    case c @ Change("minHeight", _, _) =>
      Platform.runLater {
        control.minHeight.set(c.newVal.asInstanceOf[Number].doubleValue())
      }
    case c @ Change("maxWidth", _, _) =>
      Platform.runLater {
        control.maxWidth.set(c.newVal.asInstanceOf[Number].doubleValue())
      }
    case c @ Change("maxHeight", _, _) =>
      Platform.runLater {
        control.maxHeight.set(c.newVal.asInstanceOf[Number].doubleValue())
      }
    case c @ Change("opacity", _, _) =>
      Platform.runLater {
        control.opacity.set(c.newVal.asInstanceOf[Number].doubleValue())
      }
    case c @ Change("layoutX", _, _) =>
      Platform.runLater {
        control.layoutX.set(c.newVal.asInstanceOf[Number].doubleValue())
      }
    case c @ Change("layoutY", _, _) =>
      Platform.runLater {
        control.layoutY.set(c.newVal.asInstanceOf[Number].doubleValue())
      }
    case c @ Change("rotate", _, _) =>
      Platform.runLater {
        control.rotate.set(c.newVal.asInstanceOf[Number].doubleValue())
      }
    case c @ Change("scaleX", _, _) =>
      Platform.runLater {
        control.scaleX.set(c.newVal.asInstanceOf[Number].doubleValue())
      }
    case c @ Change("scaleY", _, _) =>
      Platform.runLater {
        control.scaleY.set(c.newVal.asInstanceOf[Number].doubleValue())
      }
    case c @ Change("scaleZ", _, _) =>
      Platform.runLater {
        control.scaleZ.set(c.newVal.asInstanceOf[Number].doubleValue())
      }
    case c @ Change("translateX", _, _) =>
      Platform.runLater {
        control.translateX.set(c.newVal.asInstanceOf[Number].doubleValue())
      }
    case c @ Change("translateY", _, _) =>
      Platform.runLater {
        control.translateY.set(c.newVal.asInstanceOf[Number].doubleValue())
      }
    case c @ Change("translateZ", _, _) =>
      Platform.runLater {
        control.translateZ.set(c.newVal.asInstanceOf[Number].doubleValue())
      }
    case c @ Change("visible", _, _) =>
      Platform.runLater {
        control.visible.set(c.newVal.asInstanceOf[java.lang.Boolean].booleanValue())
      }
    case c @ Change("disable", _, _) =>
      Platform.runLater {
        control.disable.set(c.newVal.asInstanceOf[java.lang.Boolean].booleanValue())
      }
    case c @ Change("managed", _, _) =>
      Platform.runLater {
        control.managed.set(c.newVal.asInstanceOf[java.lang.Boolean].booleanValue())
      }
    case c @ Change("mouseTransparent", _, _) =>
      Platform.runLater {
        control.mouseTransparent.set(c.newVal.asInstanceOf[java.lang.Boolean].booleanValue())
      }
    case c @ Change("pickOnBounds", _, _) =>
      Platform.runLater {
        control.pickOnBounds.set(c.newVal.asInstanceOf[java.lang.Boolean].booleanValue())
      }
    case c @ Change("focusTraversable", _, _) =>
      Platform.runLater {
        control.focusTraversable.set(c.newVal.asInstanceOf[java.lang.Boolean].booleanValue())
      }
    case c @ Change("snapToPixel", _, _) =>
      Platform.runLater {
        control.snapToPixel.set(c.newVal.asInstanceOf[java.lang.Boolean].booleanValue())
      }
    case c @ Change("cache", _, _) =>
      Platform.runLater {
        control.cache.set(c.newVal.asInstanceOf[java.lang.Boolean].booleanValue())
      }
    case c @ Change("id", _, _) =>
      Platform.runLater {
        control.id.set(c.newVal.asInstanceOf[String])
      }
    case c @ Change("padding", _, _) =>
      Platform.runLater {
        control.delegate.paddingProperty().set(c.newVal.asInstanceOf[javafx.geometry.Insets])
      }
    case c @ Change("tooltip", _, _) =>
      Platform.runLater {
        control.delegate.tooltipProperty().set(c.newVal.asInstanceOf[javafx.scene.control.Tooltip])
      }
    case c @ Change("contextMenu", _, _) =>
      Platform.runLater {
        control.delegate.contextMenuProperty().set(c.newVal.asInstanceOf[javafx.scene.control.ContextMenu])
      }
    case c @ Change("cursor", _, _) =>
      Platform.runLater {
        control.delegate.cursorProperty().set(c.newVal.asInstanceOf[javafx.scene.Cursor])
      }
    case c @ Change("effect", _, _) =>
      Platform.runLater {
        control.delegate.effectProperty().set(c.newVal.asInstanceOf[javafx.scene.effect.Effect])
      }
    case c @ Change("clip", _, _) =>
      Platform.runLater {
        control.delegate.clipProperty().set(c.newVal.asInstanceOf[javafx.scene.Node])
      }
    case c @ Change("cacheHint", _, _) =>
      Platform.runLater {
        control.delegate.cacheHintProperty().set(c.newVal.asInstanceOf[javafx.scene.CacheHint])
      }
    case c @ Change("styleClass", _, _) =>
      c.newVal.asInstanceOf[ObservableBuffer.Change[String]] match {
        case Add(position, added) =>
          Platform.runLater {
            control.styleClass.insertAll(position, added)
          }
        case Remove(position, removed) =>
          Platform.runLater {
            control.styleClass.remove(position, removed.size)
          }
        case Reorder(start, end, permutation) => ???
        case Update(from, to)                 => ???
      }
  }

  override def applyChanges(control: A): Unit = {
    val revChanges = changes.reverse
    revChanges.map(applyChangesPF(control)): Unit
  }

  private val _ = prefHeight.onChange((_, oldVal, newVal) => {
    changes = Change(
      "prefHeight",
      oldVal.doubleValue(),
      newVal.doubleValue()
    ) :: changes
  })

  private val _ = style.onChange((_, oldVal, newVal) => {
    changes = Change("style", oldVal, newVal) :: changes
  })

  // TODO: this has been updated to just swallow changes to styleClass, because it appears that my Change lexicon was too simple and I hadn't realized.
  private val _ = styleClass.onChange((_, localChanges) => {
    changes = localChanges
      .flatMap {
        case Add(position, added)             => List()
        case Remove(position, removed)        => List()
        case Reorder(start, end, permutation) => List()
        case Update(from, to)                 => List()
      }
      .reverse
      .toList ::: changes
  })

  private val _ = prefWidth.onChange((_, oldVal, newVal) => {
    changes = Change("prefWidth", oldVal.doubleValue(), newVal.doubleValue()) :: changes
  })

  private val _ = minWidth.onChange((_, oldVal, newVal) => {
    changes = Change("minWidth", oldVal.doubleValue(), newVal.doubleValue()) :: changes
  })

  private val _ = minHeight.onChange((_, oldVal, newVal) => {
    changes = Change("minHeight", oldVal.doubleValue(), newVal.doubleValue()) :: changes
  })

  private val _ = maxWidth.onChange((_, oldVal, newVal) => {
    changes = Change("maxWidth", oldVal.doubleValue(), newVal.doubleValue()) :: changes
  })

  private val _ = maxHeight.onChange((_, oldVal, newVal) => {
    changes = Change("maxHeight", oldVal.doubleValue(), newVal.doubleValue()) :: changes
  })

  private val _ = opacity.onChange((_, oldVal, newVal) => {
    changes = Change("opacity", oldVal.doubleValue(), newVal.doubleValue()) :: changes
  })

  private val _ = layoutX.onChange((_, oldVal, newVal) => {
    changes = Change("layoutX", oldVal.doubleValue(), newVal.doubleValue()) :: changes
  })

  private val _ = layoutY.onChange((_, oldVal, newVal) => {
    changes = Change("layoutY", oldVal.doubleValue(), newVal.doubleValue()) :: changes
  })

  private val _ = rotate.onChange((_, oldVal, newVal) => {
    changes = Change("rotate", oldVal.doubleValue(), newVal.doubleValue()) :: changes
  })

  private val _ = scaleX.onChange((_, oldVal, newVal) => {
    changes = Change("scaleX", oldVal.doubleValue(), newVal.doubleValue()) :: changes
  })

  private val _ = scaleY.onChange((_, oldVal, newVal) => {
    changes = Change("scaleY", oldVal.doubleValue(), newVal.doubleValue()) :: changes
  })

  private val _ = scaleZ.onChange((_, oldVal, newVal) => {
    changes = Change("scaleZ", oldVal.doubleValue(), newVal.doubleValue()) :: changes
  })

  private val _ = translateX.onChange((_, oldVal, newVal) => {
    changes = Change("translateX", oldVal.doubleValue(), newVal.doubleValue()) :: changes
  })

  private val _ = translateY.onChange((_, oldVal, newVal) => {
    changes = Change("translateY", oldVal.doubleValue(), newVal.doubleValue()) :: changes
  })

  private val _ = translateZ.onChange((_, oldVal, newVal) => {
    changes = Change("translateZ", oldVal.doubleValue(), newVal.doubleValue()) :: changes
  })

  private val _ = visible.onChange((_, oldVal, newVal) => {
    changes = Change("visible", oldVal, newVal) :: changes
  })

  private val _ = disable.onChange((_, oldVal, newVal) => {
    changes = Change("disable", oldVal, newVal) :: changes
  })

  private val _ = managed.onChange((_, oldVal, newVal) => {
    changes = Change("managed", oldVal, newVal) :: changes
  })

  private val _ = mouseTransparent.onChange((_, oldVal, newVal) => {
    changes = Change("mouseTransparent", oldVal, newVal) :: changes
  })

  private val _ = pickOnBounds.onChange((_, oldVal, newVal) => {
    changes = Change("pickOnBounds", oldVal, newVal) :: changes
  })

  private val _ = focusTraversable.onChange((_, oldVal, newVal) => {
    changes = Change("focusTraversable", oldVal, newVal) :: changes
  })

  private val _ = snapToPixel.onChange((_, oldVal, newVal) => {
    changes = Change("snapToPixel", oldVal, newVal) :: changes
  })

  private val _ = cache.onChange((_, oldVal, newVal) => {
    changes = Change("cache", oldVal, newVal) :: changes
  })

  private val _ = id.onChange((_, oldVal, newVal) => {
    changes = Change("id", oldVal, newVal) :: changes
  })

  private val _ = delegate.paddingProperty().addListener(
    new javafx.beans.value.ChangeListener[AnyRef] {
      override def changed(
          obs: javafx.beans.value.ObservableValue[? <: AnyRef],
          oldVal: AnyRef,
          newVal: AnyRef
      ): Unit = changes = Change("padding", oldVal, newVal) :: changes
    }
  )

  private val _ = delegate.tooltipProperty().addListener(
    new javafx.beans.value.ChangeListener[AnyRef] {
      override def changed(
          obs: javafx.beans.value.ObservableValue[? <: AnyRef],
          oldVal: AnyRef,
          newVal: AnyRef
      ): Unit = changes = Change("tooltip", oldVal, newVal) :: changes
    }
  )

  private val _ = delegate.contextMenuProperty().addListener(
    new javafx.beans.value.ChangeListener[AnyRef] {
      override def changed(
          obs: javafx.beans.value.ObservableValue[? <: AnyRef],
          oldVal: AnyRef,
          newVal: AnyRef
      ): Unit = changes = Change("contextMenu", oldVal, newVal) :: changes
    }
  )

  private val _ = delegate.cursorProperty().addListener(
    new javafx.beans.value.ChangeListener[AnyRef] {
      override def changed(
          obs: javafx.beans.value.ObservableValue[? <: AnyRef],
          oldVal: AnyRef,
          newVal: AnyRef
      ): Unit = changes = Change("cursor", oldVal, newVal) :: changes
    }
  )

  private val _ = delegate.effectProperty().addListener(
    new javafx.beans.value.ChangeListener[AnyRef] {
      override def changed(
          obs: javafx.beans.value.ObservableValue[? <: AnyRef],
          oldVal: AnyRef,
          newVal: AnyRef
      ): Unit = changes = Change("effect", oldVal, newVal) :: changes
    }
  )

  private val _ = delegate.clipProperty().addListener(
    new javafx.beans.value.ChangeListener[AnyRef] {
      override def changed(
          obs: javafx.beans.value.ObservableValue[? <: AnyRef],
          oldVal: AnyRef,
          newVal: AnyRef
      ): Unit = changes = Change("clip", oldVal, newVal) :: changes
    }
  )

  private val _ = delegate.cacheHintProperty().addListener(
    new javafx.beans.value.ChangeListener[AnyRef] {
      override def changed(
          obs: javafx.beans.value.ObservableValue[? <: AnyRef],
          oldVal: AnyRef,
          newVal: AnyRef
      ): Unit = changes = Change("cacheHint", oldVal, newVal) :: changes
    }
  )

  /* Complex changes that Change(propertyName, oldVal, newVal) cannot yet
   * express and which must be considered when expanding the design of Change:
   *  - styleClass (ObservableBuffer): Add/Remove/Reorder/Update; replay of
   *    Reorder and Update is not implemented, and removals of entries not
   *    present on the real control are ambiguous.
   *  - properties (ObservableMap): per-key put/remove changes.
   *  - skin: a Skin is bound to the proxy instance and cannot be moved.
   *  - eventHandlers/filters (onMouseClicked, onKeyPressed, ...): functions,
   *    the replay would need to re-register on the real control.
   *  - transforms, nodeOrientation, blendMode, inputMethodRequests,
   *    accessibility properties, and any bound (not set) property: these are
   *    ObservableList, enum or binding semantics where the old/new pair is not
   *    sufficient.
   */

  /** Calling this is not supported from the FXMonad system.
    *
    * @param tail
    * @return
    */
  override def buildEventDispatchChain(
      tail: scalafx.event.EventDispatchChain
  ): scalafx.event.EventDispatchChain = throwError

  /** Calling this is not supported from the FXMonad system.
    */
  override def autosize(): Unit = throwError
  // TODO: This is too boring and I'm moving on to stuff I want to do.
}

class TextFieldProxy extends TextField with SFXProxy[TextField] {

  private val _ = text.onChange((_, oldVal, newVal) => {
    changes = Change("text", oldVal, newVal) :: changes
  })

  override protected def applyChangesPF(
      control: TextField
  ): PartialFunction[Change, Unit] = {
    val localChange: PartialFunction[Change, Unit] = {
      case c @ Change("text", _, _) =>
        Platform.runLater {
          control.text() = c.newVal.asInstanceOf[String]
          println(s"Control: ${control.text()}")
        }
    }
    localChange.orElse(super.applyChangesPF(control))
  }
}

class CheckBoxProxy extends CheckBox with SFXProxy[CheckBox] {
  private val _ = selected.onChange((_, oldVal, newVal) => {
    changes = Change("selected", oldVal, newVal) :: changes
  })

  override protected def applyChangesPF(
      control: sfxc.CheckBox
  ): PartialFunction[Change, Unit] = {
    val localChange: PartialFunction[Change, Unit] = {
      case c @ Change("selected", _, _) =>
        Platform.runLater {
          control.selected() = c.newVal.asInstanceOf[Boolean]
        }
    }
    localChange.orElse(super.applyChangesPF(control))
  }
}

class RadioButtonProxy extends RadioButton with SFXProxy[RadioButton] {
  private val _ = selected.onChange((_, oldVal, newVal) => {
    changes = Change("selected", oldVal, newVal) :: changes
  })

  override protected def applyChangesPF(
      control: sfxc.RadioButton
  ): PartialFunction[Change, Unit] = {
    val localChange: PartialFunction[Change, Unit] = {
      case c @ Change("selected", _, _) =>
        Platform.runLater {
          control.selected() = c.newVal.asInstanceOf[Boolean]
        }
    }
    localChange.orElse(super.applyChangesPF(control))
  }
}

class SliderProxy extends Slider with SFXProxy[Slider] {
  private val _ = value.onChange((_, oldVal, newVal) => {
    changes = Change("value", oldVal, newVal) :: changes
  })

  override protected def applyChangesPF(
      control: sfxc.Slider
  ): PartialFunction[Change, Unit] = {
    val localChange: PartialFunction[Change, Unit] = {
      case c @ Change("value", _, _) =>
        Platform.runLater {
          control.value() = c.newVal.asInstanceOf[Double]
        }
    }
    localChange.orElse(super.applyChangesPF(control))
  }
}

class LabelProxy extends Label with SFXProxy[Label] {
  private val _ = text.onChange((_, oldVal, newVal) => {
    changes = Change("text", oldVal, newVal) :: changes
  })

  override protected def applyChangesPF(
      control: sfxc.Label
  ): PartialFunction[Change, Unit] = {
    val localChange: PartialFunction[Change, Unit] = {
      case c @ Change("text", _, _) =>
        Platform.runLater {
          control.text() = c.newVal.asInstanceOf[String]
        }
    }
    localChange.orElse(super.applyChangesPF(control))
  }
}

class ColorPickerProxy extends sfxc.ColorPicker with SFXProxy[ColorPicker] {
  private val _ = this.value.onChange((_, oldVal, newVal) => {
    changes = Change("value", oldVal, newVal) :: changes
  })

  override protected def applyChangesPF(
      control: ColorPicker
  ): PartialFunction[Change, Unit] = {
    val localChange: PartialFunction[Change, Unit] = {
      case c @ Change("value", _, _) =>
        Platform.runLater {
          control.value() = c.newVal.asInstanceOf[Color]
        }
    }
    localChange.orElse(super.applyChangesPF(control))
  }
}

// TODO: This whole class, because ToggleGroup is not a control, probably requires a bunch of more implementation, namely subscribing to all the properties and handling all of the possible changes.
class ToggleGroupProxy extends sfxc.ToggleGroup with Proxy[ToggleGroup] {
  val selectedGroupSubscription =
    this.selectedToggle.onChange((_, oldVal, newVal) => {
      changes = Change("selectedToggle", oldVal, newVal) :: changes
    })

  override def applyChanges(control: ToggleGroup): Unit = {
    this.changes.reverse.map(_ match {
      case c @ Change("selectedToggle", _, _) =>
        Platform.runLater {
          control.toggles
            .find(
              _.getUserData()
                .equals(c.newVal.asInstanceOf[Toggle].getUserData())
            )
            .map(_.setSelected(true)): Unit
        }
      case c =>
        println(
          s"Failed to match change ${c.toString()}. Probably not implemented."
        )
    }): Unit
  }

}
