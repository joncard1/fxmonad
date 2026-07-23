package fxmonad.sfx

import scala.util.Using.Releasable
import scalafx.beans.value.ObservableValue
import scalafx.scene.control.TextField
import scalafx.scene.control.CheckBox
import fxmonad._

// TODO: This class seems more aware of ScalaFX than I'd like. It's possible that the current implementation of Control#update(...) should be pushed down to SFXControl, and some other method would be used to bind controls that are not ScalaFX-based to other Control types.
// TODO: Right now, this would be complicated as Control#defaultProperty is a ScalaFX property bean, but that may not be a problem, as other systems could use that property type. But it might use another mechanism than ControlBinder which would not need to know about SFXProxy, etc.

object ControlBinder {
    given Releasable[ControlBinder[?]] = new Releasable[ControlBinder[?]] {
        override def release(resource: ControlBinder[?]): Unit = resource.dispose()
    }
}

trait ControlBinder[A](outputControl: Control[A]) {
    protected[fxmonad] def updateValueInner(): Control[A]
    protected[fxmonad] def updateValue(): Unit = {
        val newC = updateValueInner()
        def defaultUpdateBehavior() = {
            outputControl.defaultProperty() = newC.defaultProperty()
        }
        if (
            outputControl.isInstanceOf[ControlContainer[A]] &&
            outputControl.asInstanceOf[ControlContainer[A]].control.isInstanceOf[SFXControl[?, ?, ?]] &&
            newC.isInstanceOf[SFXControl[?, ?, ?]] &&
            (outputControl.asInstanceOf[ControlContainer[A]].control.asInstanceOf[SFXControl[?, ?, ?]].getClass != newC.asInstanceOf[SFXControl[?, ?, ?]].control.getClass)
        ) {
            outputControl.asInstanceOf[ControlContainer[A]].replaceControl(newC)
            // TODO: This now broken because the binder is not bound to the new control
        } else if(
            outputControl.isInstanceOf[ControlContainer[A]] &&
            outputControl.asInstanceOf[ControlContainer[A]].control.isInstanceOf[SFXControl[A, ?, ?]] &&
            newC.isInstanceOf[SFXControl[A, ?, ?]] &&
            newC.asInstanceOf[SFXControl[A, ?, ?]].control.isInstanceOf[SFXProxy[?]]
        ) {
            // TODO: I'd also like to check that the type of outputControl.control.control is the same type as newC.control#SFProxy[here]
            (outputControl.asInstanceOf[ControlContainer[A]].control.asInstanceOf[SFXControl[?, ?, ?]].control, newC.asInstanceOf[SFXControl[?, ?, ?]].control) match {
                case (c1: TextField, c2: TextFieldProxy) => c2.applyChanges(c1)
                case (c1: CheckBox, c2: CheckBoxProxy) => c2.applyChanges(c1)
                case (_, _) => defaultUpdateBehavior()
            }
        // The thing is, this would assume that all of the controls are SFXControl subclasses, which they are not.
        // TODO: Else, the new control must be a proxy of some kind, because I can't guarantee the various references.
        // If it's not a ControlContainer, then the type argument of the JFXProxy must be the same as the type of the outputControl. Then, we can run proxy.applyChanges(outputControl).
        } /*else if(newC has .control and .control is JFProxy) {
        // Else, just set the default property of the outputControl to the property of the newC.
        }*/
        else {
            defaultUpdateBehavior()
        }
    }
    def dispose(): Unit
}

class ControlBinder1[A, B](c1: Control[A], outputControl: Control[B], f: (A) => Control[B]) extends ControlBinder[B](outputControl) {

    override protected[fxmonad] def updateValueInner(): Control[B] = {
        c1.flatMap(x1 => {
            f(x1)
        })
    }
    val subscription1 = c1.defaultProperty.onChange({
        updateValue()
        ()
    })
    
    override def dispose(): Unit = {
        subscription1.cancel()
    }
}

class ControlBinder2[A, B, C](c1: Control[A],  c2: Control[B], outputControl: Control[C], f: (A, B) => Control[C]) extends ControlBinder[C](outputControl) {
    protected[fxmonad] def updateValueInner(): Control[C] = {
        c1.flatMap(x1 => c2.flatMap(x2 => {
            f(x1, x2)
        }))
    }
    val subscription1 = c1.defaultProperty.onChange({
        updateValue()
        ()
    })
    val subscription2 = c2.defaultProperty.onChange({
        updateValue()
        ()
    })

    override def dispose(): Unit = {
        subscription1.cancel()
        subscription2.cancel()
    }
}

class ControlBinder3[A, B, C, D](c1: Control[A], c2: Control[B], c3: Control[C], outputControl: Control[D], f: (A, B, C) => Control[D]) extends ControlBinder[D](outputControl) {
    protected[fxmonad] def updateValueInner(): Control[D] = {
        c1.flatMap(x1 => c2.flatMap(x2 => c3.flatMap(x3 => {
            f(x1, x2, x3)
        })))
    }

    val subscription1 = c1.defaultProperty.onChange({
        updateValue()
        ()
    })
    val subscription2 = c2.defaultProperty.onChange({
        updateValue()
        ()
    })
    val subscription3 = c3.defaultProperty.onChange({
        updateValue()
        ()
    })
    override def dispose(): Unit = {
        subscription1.cancel()
        subscription2.cancel()
        subscription3.cancel()
    }
}
