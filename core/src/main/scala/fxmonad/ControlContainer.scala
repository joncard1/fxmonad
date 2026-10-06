package fxmonad

import scalafx.beans.property.Property
import scalafx.event.subscriptions.Subscription
import javafx.scene.{layout => jfxl}

import scala.reflect.ClassTag
import scalafx.beans.property.ObjectProperty
import fxmonad.Control.MountContext

object ControlContainer {
  def apply[A](property: Property[A, ?], control: Control[A]) = {
    SFXControlContainer(property, control)
  }

  def apply[A: PropertyConstructor](control: Control[A]) = {
    val constructor = summon[PropertyConstructor[A]]
    SFXControlContainer(constructor(), control)
  }
}

/** Container of a single [[Control]] instance that can act interchange
  * different Control instances of the same type (Control[Int] for Control[Int],
  * or Control[String] for Control[String]). The system backing the Control does
  * not need to be the same (it can be used to replace a ScalaFX-based control
  * like a Slider for an HID control like a slider on a sound board, for
  * instance), although this may lead to lost information such as the mounting
  * location within the ScalaFX node tree. Subclasses with awareness of the
  * contexts of the two controls may work more reliably.
  *
  * Currently, all controls created by the macro [[fxmonad.FXMonad]] and
  * [[fxmonad.FXEmitter]] are automatically wrapped in a default
  * [[ControlContainer]] implementation. In future development, it may be
  * possible to customize the constructor used to create the container.
  */
// TODO: There probably needs to be a true default implementation that swaps out two controls but doesn't bother with the UI system.
trait ControlContainer[COut] extends Control[COut] {
  // TODO: Probably asking for the same trouble here that I had with clearError, showError, etc.
  protected[fxmonad] var wrappedControl: Control[COut]
  protected[fxmonad] def replaceControl(newControl: Control[COut]): Unit
  private var wrappedSubscription: Option[Subscription] = None

  override protected[fxmonad] def updateFrom
      : PartialFunction[Control[COut], Unit] = {
    case source: ControlContainer[?] =>
      wrappedControl.updateFrom(
        source.wrappedControl.asInstanceOf[Control[COut]]
      )
    case source => wrappedControl.updateFrom(source)
  }

  // override def isProxy: Boolean = wrappedControl.isProxy

  protected def setWrappedControl(control: Control[COut]): Unit = {
    wrappedSubscription.fold(())(_.cancel())
    wrappedControl = control
    wrappedSubscription = Some(
      wrappedControl.defaultProperty.onChange((_, _, _) => {
        defaultProperty() = control.defaultProperty()
      })
    )
    defaultProperty() = control.defaultProperty()
  }
}

/** The defacto default [[ControlContainer]] implementation. This container acts
  * as a placeholder for where two controls from different contexts can be
  * interchanged. However, it does not store the previous context into which to
  * remount a control, so if mounting one control will cause a loss of reference
  * to the context in which a control was mounted, it will not be able to be
  * remounted. For instance, if a ScalaFX Slider control is replaced by an HID
  * device, the Slider will not be able to be remounted when exchanged back as
  * it won't be defined where in the ScalaFX control tree to mount it. See
  * [[SFXControlContainer]] for an implementation of [[ControlContainer]] that
  * holds a place in the ScalaFX UI tree at which to mount UI elements even when
  * replaced by other [[Control]] instances temporarily.
  */
// TODO: Adding mountControl and unmountControl may have made this redundant. Consider whether to rename this (I find it confusing) or move this up to make ControlContainer instantiable.
class SFXControlContainer[COut](
    override val defaultProperty: Property[COut, ?],
    val control: Control[COut]
) extends ControlContainer[COut] {

  protected[fxmonad] var wrappedControl: Control[COut] =
    scala.compiletime.uninitialized
  setWrappedControl(control)
  private val _ = defaultProperty.onChange((_, _, _) => {
    wrappedControl.defaultProperty() = defaultProperty()
  })

  override def showError(errorMsg: String): Unit = {
    wrappedControl.showError(errorMsg)
  }

  override def clearError(): Unit = {
    wrappedControl.clearError()
  }

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
  override protected[fxmonad] def replaceControl(
      newControl: Control[COut]
  ): Unit = {
    if (newControl.isProxy) {
      wrappedControl.updateFrom(newControl)
    } else {
      val mountContext = wrappedControl.unmountControl()
      newControl.mountControl(mountContext)
    }
    if (!newControl.isProxy) {
      setWrappedControl(newControl)
    }
  }

  // TODO: This may not be good, because a child context would contain the index at which it was mounted, and if it changed much it would mount in the wrong place. Maybe that's someone else's problem.
  var previousContext: Option[MountContext] = None

  override def mountControl(context: Option[Control.MountContext]): Unit = {
    context.fold(wrappedControl.mountControl(previousContext))(_ =>
      wrappedControl.mountControl(context)
    )
    previousContext = None
  }
  override def unmountControl(): Option[Control.MountContext] =
    previousContext = wrappedControl.unmountControl()
    previousContext
}

case class SFXMountContext(parent: javafx.scene.Parent, index: Int)
    extends Control.MountContext

/** An implementation of [[ControlContainer]] that reserves a place in the
  * JavaFX UI node tree. It can act as the location where different elements can
  * be interchanged, but, unlike [[SFXControlContainer]], it can also replace
  * the control with a non-JavaFX [[Control]] and re-mount a JavaFX node element
  * if the contained control is replaced again with a JavaFX-based control, the
  * new control can be mounted.)
  */
// TODO: This should be moved into the sfx package.
// TODO: If this is not given a control (which should supported behavior), this will create problems because wrappedControl isn't an Option and yet it's uninitialized.
class ControlPane[A] extends jfxl.Pane with ControlContainer[A] {

  override def mountControl(context: Option[Control.MountContext]): Unit =
    context match {
      case None => println("Not given a context to mount to.")
      case Some(SFXMountContext(parent, index)) =>
        parent.getChildrenUnmodifiable().add(index, this)
      case Some(_) => println("Given an incompatible context to mount to.")
    }

  override def unmountControl(): Option[Control.MountContext] = {
    // TODO: Do I need to change ControlPane to ScalaFX Pane? I think it doesn't have to be; FXML should be able to create a ScalaFX object. But can my monad handle that?
    val parent = this.getParent()
    val index = parent.getChildrenUnmodifiable().indexOf(this)
    val removed = parent.getChildrenUnmodifiable().remove(this)
    if (removed) {
      Option(SFXMountContext(parent, index))
    } else {
      None
    }
  }

  override def showError(errorMsg: String): Unit = {
    wrappedControl.showError(errorMsg)
  }

  override def clearError(): Unit = {
    wrappedControl.clearError()
  }

  // TODO: The point of this is that it provides its own mount context for SFXControls, so it should always unmount the contained control and provide itself as the mount context for the new control.
  override protected[fxmonad] def replaceControl(
      newControl: Control[A]
  ): Unit = {
    // TODO: Think through whether this should be done before or after the successful swap of the controls.
    // val isProxy = isProxyControl(newControl)
    // if (!isProxy) {
    // The two ways the value of a control are updated are: 1. if given a proxy, apply the changes in the proxy or 2. if given a non-proxy, replace the control. Containers are treated as
    if (newControl.isProxy) {
      wrappedControl.updateFrom(newControl)
    } else {
      val _ = wrappedControl.unmountControl()
      newControl.mountControl(Some(SFXMountContext(this, 0)))
    }
    if (
      !newControl.isProxy /*|| newControl.isInstanceOf[ControlContainer[?]]*/
    ) {
      setWrappedControl(newControl)
    }
    /*} else {
      if (!newControl.isInstanceOf[Proxy[?]]) {
        setWrappedControl(newControl)
      }
      wrappedControl.updateFrom(newControl)
    }*/
    /*
    Platform.runLater {
      this.getChildren().clear()
      def addControl(newC: Control[A]): Boolean = {
        newC match {
          case nc: ControlPane[A] =>
            this.getChildren().add(nc)
          case nc: SFXControl[?, ?, ?] =>
            this.getChildren().add(nc.control)
          case nc: ControlContainer[A] =>
            addControl(nc.wrappedControl)
          case _ =>
            //println("Discarding new control in ControlPane. Presumably it's a non-visible control.")
            true
        }
      }
      val success = addControl(newControl)
      if (!success) {
        println("Some kind of failure inserting control to ControlPane.")
      }
    }
     */
  }

  protected[fxmonad] var wrappedControl: Control[A] =
    scala.compiletime.uninitialized

  override val defaultProperty: Property[A, ?] = new ObjectProperty()

  def initializeContainer(using tag: ClassTag[A]): Unit = {
    import scala.collection.JavaConverters.given
    if (this.getChildren().size() > 1) {
      throw Exception(
        s"The ControlContainer ${this.getId()} needs to be initialized with 1 or fewer children."
      )
    }
    this
      .getChildren()
      .asScala
      .map(child => {
        // TODO: Look again at this cast. Could be trouble. I think the issue is it can compile because the type of A gets erased so it casts to "Control" but there's a possibility of assigning Control[String] to Control[Int], right? It shouldn't, because after getting from Class[A] from lookups, the controls in there SHOULD all be Control[A], but it's not strict. Will it throw a runtime error if that's attempted, if the type of A is erased? Is it possible to put a type parameter on lookupControl?
        try {
          val control = Control
            .lookupControl(tag.runtimeClass, child)
            .asInstanceOf[Control[A]]
          setWrappedControl(control)
        } catch {
          case e: ClassCastException =>
            println(
              s"Swallowing a class cast exception in ControlPane, but it'll probably be trouble later. ${e.toString()}"
            )
        }
      }): Unit
  }

}
