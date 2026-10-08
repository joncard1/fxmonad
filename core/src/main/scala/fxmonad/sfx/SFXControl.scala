package fxmonad.sfx

import fxmonad.ControlBase
import fxmonad.Conversion
import fxmonad.Control
import scalafx.application.Platform
import scalafx.scene.control.Tooltip
import fxmonad.SFXMountContext
import javafx.scene.layout.Pane

/** A parent class for controls based on ScalaFX controls.
  *
  * @param control
  *   The ScalaFX control wrapped by this object.
  * @param inConversion
  *   A utility to convert value of the type exposed by this monad to the naive
  *   type of the control.
  * @param outConversion
  *   A utility to convert a value from the naive type of the control to the
  *   type exposed by this monad.jk
  */
abstract class SFXControl[
    COut,
    CIn,
    InnerControl <: scalafx.scene.control.Control
](val control: InnerControl)(using
    inConversion: Conversion[COut, CIn],
    outConversion: Conversion[CIn, COut]
) extends ControlBase(using inConversion, outConversion) {
  override def mountControl(context: Option[Control.MountContext]) =
    context match {
      case None => println("No context to match to")
      case Some(SFXMountContext(parent: Pane, index)) => {
        if (!control.isInstanceOf[SFXProxy[?]]) { // Defensively, check and don't mount a proxy. There are boundary consitions where it might end up here, and a proxy should never be miunted.
          Platform.runLater {
            parent.getChildren().add(index, control)
          }
        }
      }
      case _ => println("Incompatible context provided to mount SFXControl")
    }

  // TODO: There is a potential bug here. The read of control.parent and the index could happen outside the FX thread, so they may not be the same as when the deferred unmount actually happens. However, they need to be read before returning from this function. The actual removal doesn't require knowing the index, so that's no a source of defect, but the parent may have changed and whether the control is still there may have changed, and the context passed up and back down to #mountControl may not restore a control to the same place.
  override def unmountControl(): Option[Control.MountContext] = {
    control.parent() match {
      case null       => None
      case pane: Pane =>
        val index = pane.getChildren().indexOf(control)
        Platform.runLater { // TODO: Anticipating timing issues here
          pane.getChildren().remove(control)
        }
        Some(SFXMountContext(pane, index))
      case _ => None
    }
  }

  override def isProxy = control match {
    case _: SFXProxy[?] => true
    case _              => false
  }
}

trait TooltipValidationErrorStrategy[
    COut,
    CIn,
    InnerControl <: scalafx.scene.control.Control
] { this: SFXControl[COut, CIn, InnerControl] =>
  override def clearError(): Unit = {
    control.tooltip() = null
    Platform.runLater {
      control.styleClass.removeAll("error")
    }
  }

  override def showError(errorMsg: String): Unit = {
    control.tooltip() = Tooltip(errorMsg)
    Platform.runLater {
      control.styleClass.add("error")
    }
  }
}
