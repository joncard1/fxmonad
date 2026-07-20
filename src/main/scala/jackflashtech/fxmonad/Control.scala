package jackflashtech.fxmonad

import cats.Monad
import scalafx.scene.control.TextField
import scalafx.scene.control.CheckBox
import scalafx.beans.property.Property
import scala.util.Using.Releasable
import scalafx.beans.value.ObservableValue
import scalafx.beans.property.IntegerProperty
import scalafx.beans.property.StringProperty
import scalafx.scene.control.Tooltip
import scala.reflect.ClassTag
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import scalafx.beans.property.BooleanProperty
import jackflashtech.fxmonad.sfx._
import javafx.scene.layout.Pane
import scalafx.application.Platform
import scalafx.event.subscriptions.Subscription
import jackflashtech.{fxmonad => clearError}

object Control {
    // TODO: I wonder if I'd prefer to define my own type of thing like Conversion but which was MyConversion[A, B] = (A) => Try[B] or (A) => Either[String, B] so I could define the error message in the converter instead of the control.

    private def selfConversion[A]() : Conversion[A, A] = (x: A) => x
    given Conversion[Boolean, Boolean] = selfConversion()
    given Conversion[String, String] = selfConversion()
    given Conversion[Double, Double] = selfConversion()
    given Conversion[String, Int] = (x: String) => x.toInt
    given Conversion[Int, String] = (x: Int) => x.toString()
    given Conversion[Int, Double] = (x: Int) => x.toDouble
    given Conversion[Double, Int] = (x: Double) => x.toInt
    given Conversion[String, Boolean] = (x: String) => x match {
        case "true" => true
        case _ => false
    }
    given Conversion[Int, Boolean] = (x: Int) => x match {
        case 0 => false
        case _ => true
    }
    given Conversion[Boolean, Int] = (x: Boolean) => if (x) then 1 else 0

    given Conversion[Boolean, String] = (x: Boolean) => x.toString()

}

abstract class ControlBase[COut, CIn](using inConversion: Conversion[COut, CIn], outConversion: Conversion[CIn, COut]) extends Control[COut] {
    /**
      * Utility class to update the property associated with this class using the value of the contained control.
      *
      * @param newVal
      */
    protected def updateProperty(newVal: CIn) = {
        if (newVal != null) { // TODO: The else branch
            Try(outConversion(newVal)) match {
                case Success(null) =>
                    showError("The control value was set to null")
                case Success(nv) =>
                    clearError()
                    defaultProperty() = nv
                case Failure(e) =>
                    showError("There was a conversion error")
            }
        }
    }
}

abstract class Control[COut] { 
    protected var binder: Option[ControlBinder[COut]] = None

    val defaultProperty: Property[COut, ?]

    protected[fxmonad] def showError(errorMsg: String): Unit

    protected[fxmonad] def clearError(): Unit


    //def map[B](f: (COut) => B): Control[B, ?] = new CarrierControl(f(defaultProperty()))
    def flatMap[B](f: (x: COut) => Control[B]): Control[B] = f(defaultProperty())

    def apply(): COut = defaultProperty()
    def update(newVal: COut) = defaultProperty() = newVal

    def update[B](control1: Control[B], f: B => Control[COut]): Unit = {
        binder = {
            binder match {
                case None =>
                case Some(bind) => bind.dispose()
            }
            Some(new ControlBinder1(control1, this, f))
        }
        binder.flatMap(x => Option(x.updateValue())).get
    }

    def update[B, C](control1: Control[B], control2: Control[C], f: (B, C) => Control[COut]): Unit = {
        binder = {
            binder match {
                case None =>
                case Some(bind) => bind.dispose()
            }
            Some(new ControlBinder2(control1, control2, this, f))
        }
        binder.flatMap(x => Option(x.updateValue())).get
    }

    def update[B, C, D](control1: Control[B], control2: Control[C], control3: Control[D], f: (B, C, D) => Control[COut]): Unit = {
        binder = {
            binder match {
                case None =>
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
class ControlContainer[COut](override val defaultProperty: Property[COut, ?],  val control: Control[COut]) extends Control[COut] {

    private var wrappedControl: Control[COut] = scala.compiletime.uninitialized
    private var wrappedSubscription: Option[Subscription] = None

    private def setWrappedControl(control: Control[COut]) = {
        wrappedSubscription.map(_.cancel())
        wrappedControl = control
        wrappedSubscription = Some(wrappedControl.defaultProperty.onChange((_, _, _) => {
            // TODO: This is broken because the ScalaFX wrapper around the JavaFX properties isn't broken-ish
            defaultProperty() = control.defaultProperty()
        }))
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

    // TODO: It seems both 1. a problem and 2. necessary for the internal type to change. The only impact, really, is to use a different conversion internally and to change the signature of updateProperty. Does it need to be part of the external type declaration?
    protected[fxmonad] def replaceControl(newControl: Control[COut]) = {
        // TODO: If the new control has a different inner type than control, replace the wrapped control in the JavaFX tree with the new control and keep the passed-in control as the new wrapped control. Which seems like it'll probably be a nightmare.
        // TODO: This is a lot of the same logic as in ControlBinder#update, except for ControlBinder deferring to ControlContainer.
        
        def defaultBehavior() = {
            wrappedControl.defaultProperty() = newControl.defaultProperty()
        }

        // TODO: This is at least the beginning of the checks that are needed.
        // TODO: Add checks if newControl isn't Control[COut, CIn], it won't work to replace the control.
        // TODO: Which, of course, can't be done because of type erasure
        if((wrappedControl.isInstanceOf[SFXControl[?, ?, ?]]) &&
            (newControl.isInstanceOf[SFXControl[COut, ?, ?]]) &&
            (wrappedControl.asInstanceOf[SFXControl[?, ?, ?]].control.getClass != newControl.asInstanceOf[SFXControl[COut, ?, ?]].control.getClass()) &&
            !(newControl.asInstanceOf[SFXControl[?, ?, ?]].control.isInstanceOf[SFXProxy[?]])) {
            val wrappedControlSfx = wrappedControl.asInstanceOf[SFXControl[?, ?, ?]]
            val newControlSfx = newControl.asInstanceOf[SFXControl[COut, ?, ?]]
            val wrappedControlParent = wrappedControlSfx.control.parent()
            if (wrappedControlParent.isInstanceOf[Pane]) {
                val wrappedControlPane = wrappedControlParent.asInstanceOf[Pane]
                Platform.runLater {
                    // TODO: This should replace the other control in the same index
                    val index = wrappedControlPane.getChildren().indexOf(wrappedControlSfx.control.delegate)
                    if (index > -1) {
                        wrappedControlPane.getChildren().remove(wrappedControlSfx.control.delegate)
                        wrappedControlPane.getChildren().add(index, newControlSfx.control.delegate)
                    } else {/* TODO: This probably needs something more. */}
                    
                    //wrappedControlPane.getChildren().forEach(x => println(x.toString()))
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

// TODO: This should probably be moved somewhere else.
abstract class SFXControl[COut, CIn, InnerControl <: scalafx.scene.control.Control](val control: InnerControl)(using inConversion: Conversion[COut, CIn], outConversion: Conversion[CIn, COut]) extends ControlBase(using inConversion, outConversion)