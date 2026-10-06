package fxmonad.sfx

import fxmonad.PropertyConstructor
import scalafx.scene.control.ToggleGroup
import scalafx.beans.property.Property
import fxmonad.Conversion
import fxmonad.ControlBase
import fxmonad.Control

// TODO: This may have further issues with:
//  1. null user data
//  2. when a selection does not exist
// These are edge cases that are not properly tested in the BraceletApp test application, and how they work will be impacted by the PropertyConstructor and Conversion classes there; the functionality here should not depend on those objects, and this class should support more than those cases.
object ToggleGroupControl {
  def apply[A: PropertyConstructor]()(using
      inConversion: Conversion[A, Object],
      outCoversion: Conversion[Object, A]
  ): ToggleGroupControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    new ToggleGroupControl(constructor())
  }

  def apply[A: PropertyConstructor](initialValue: A)(using
      inConversion: Conversion[A, Object],
      outConversion: Conversion[Object, A]
  ): ToggleGroupControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    val newC = new ToggleGroupControl(constructor())
    newC() = initialValue
    newC
  }

  def apply[A: PropertyConstructor](control: ToggleGroup)(using
      inConversion: Conversion[A, Object],
      outConversion: Conversion[Object, A]
  ): ToggleGroupControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    new ToggleGroupControl(constructor(), control)
  }

  def apply[A: PropertyConstructor](initialValue: A, control: ToggleGroup)(using
      inConversion: Conversion[A, Object],
      outConversion: Conversion[Object, A]
  ): ToggleGroupControl[A] = {
    val constructor = summon[PropertyConstructor[A]]
    val newC = new ToggleGroupControl(constructor(), control)
    newC() = initialValue
    newC
  }
}

class ToggleGroupControl[COut](
    override val defaultProperty: Property[COut, ?],
    val control: ToggleGroup = new ToggleGroupProxy()
)(using
    inConversion: Conversion[COut, Object],
    outConversion: Conversion[Object, COut]
) extends ControlBase[COut, Object](using
      inConversion,
      outConversion
    ) {
  control.selectedToggle.onChange((_, _, _) =>
    updateProperty(control.selectedToggle().getUserData())
  ): Unit

  override def clearError(): Unit = {}
  override def showError(errorMsg: String): Unit = {
    // TODO: It's not clear how a group should report a validation error, especially considering Toggle controls shouldn't allow invalid selections anyway. It might be safe to swallow the exception, but it would indicate a misconfiguration the developer should know about, so it should be surfaced somehow.
    //  If this does end up requiring something, don't forget to update clearError.
    println(s"Not sure what to do about this: showError: ${errorMsg}")
  }

  defaultProperty.onChange((_, _, _) => {
    inConversion(defaultProperty()) match {
      case Right(null) =>
        showError("The selected value was set to null.")
      case Right(nv) =>
        control.toggles
          .find(_.getUserData().equals(nv))
          .map(_.setSelected(true)): Unit
      case Left(msg) =>
        showError(msg)
    }
  }): Unit

  updateProperty(control.selectedToggle().getUserData())

  override def mountControl(context: Option[Control.MountContext]): Unit = ???
  override def unmountControl(): Option[Control.MountContext] = ???
}
