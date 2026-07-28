package fxmonad.sfx

import fxmonad.ControlBase
import fxmonad.Conversion
import scalafx.beans.property.Property
import fxmonad.Control

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
](val defaultProperty: Property[COut, ?], val control: InnerControl)(using
    inConversion: Conversion[COut, CIn],
    outConversion: Conversion[CIn, COut]
) extends ControlBase(using inConversion, outConversion) {
  override def apply(): COut = defaultProperty()
  override def update(newVal: COut): Unit = defaultProperty() = newVal

  override def update[B](control1: Control[B], f: B => Control[COut]): Unit = {
    if (!control1.isInstanceOf[SFXControl[B, ?, ?]]) {
        throw new Exception("Currently, an ScalaFX control cannot be bound to other kinds of controls. Control1 is not an SFXControl.")
    }
    binder = {
      binder match {
        case None       =>
        case Some(bind) => bind.dispose()
      }
      Some(new ControlBinder1(control1.asInstanceOf[SFXControl[B, ?, ?]], this, f))
    }
    binder.flatMap(x => Option(x.updateValue())).get
  }

  override def update[B, C](
      control1: Control[B],
      control2: Control[C],
      f: (B, C) => Control[COut]
  ): Unit = {
    if (!control1.isInstanceOf[SFXControl[B, ?, ?]]) {
        throw new Exception("Currently, an ScalaFX control cannot be bound to other kinds of controls. Control 1 is not an SFXControl.")
    }
    if (!control2.isInstanceOf[SFXControl[C, ?, ?]]) {
        throw new Exception("Currently, an ScalaFX control cannot be bound to other kinds of controls. Control 2 is not an SFXControl.")
    }
    binder = {
      binder match {
        case None       =>
        case Some(bind) => bind.dispose()
      }
      Some(new ControlBinder2(control1.asInstanceOf[SFXControl[B, ?, ?]], control2.asInstanceOf[SFXControl[C, ?, ?]], this, f))
    }
    binder.flatMap(x => Option(x.updateValue())).get
  }

  override def update[B, C, D](control1: Control[B], control2: Control[C], control3: Control[D], f: (B, C, D) => Control[COut]): Unit  = {
    if (!control1.isInstanceOf[SFXControl[B, ?, ?]]) {
        throw new Exception("Currently, an ScalaFX control cannot be bound to other kinds of controls. Control 1 is not an SFXControl.")
    }
    if (!control2.isInstanceOf[SFXControl[C, ?, ?]]) {
        throw new Exception("Currently, an ScalaFX control cannot be bound to other kinds of controls. Control 2 is not an SFXControl.")
    }
    if (!control3.isInstanceOf[SFXControl[D, ?, ?]]) {
        throw new Exception("Currently, an ScalaFX control cannot be bound to other kinds of controls. Control 3 is not an SFXControl.")
    }
    binder = {
      binder match {
        case None       =>
        case Some(bind) => bind.dispose()
      }
      Some(new ControlBinder3(control1.asInstanceOf[SFXControl[B, ?, ?]], control2.asInstanceOf[SFXControl[C, ?, ?]], control3.asInstanceOf[SFXControl[D, ?, ?]], this, f))
    }
    binder.flatMap(x => Option(x.updateValue())).get
  }
}
