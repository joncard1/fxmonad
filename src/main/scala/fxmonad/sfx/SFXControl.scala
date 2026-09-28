package fxmonad.sfx

import fxmonad.ControlBase
import fxmonad.Conversion
import java.net.URL
import fxmonad.Control
import javafx.fxml.FXMLLoader
import fxmonad.FXMonad
import scala.reflect.ClassTag
import scala.annotation.experimental

// TODO: Review whether the refactoring from Control to Node is still desirable now that the decision to support Region- and Node-type customizations by expecting the controller to be a Control[?] rather than the Node itself.
// It's not clear to me why this tag was only required when I changed the signature of lookups to Node from Control.
@experimental
object SFXControl {
    def defaultBehaviorFactory(c: Class[?]): Object = ???

    def fromFXML[COut](path: URL)(using ct: ClassTag[COut]): Control[COut] = {
        val loader = new FXMLLoader(path)
        // TODO: Resolve the situation of using a custom factory. Probably, it should become a context parameter of some kind.
        loader.setControllerFactory(defaultBehaviorFactory)
        val newControl = loader.load[javafx.scene.control.Control]()
        // TODO: Fix these exception messages. They aren't good.
        FXMonad.lookups.get().get(ct.getClass())
            .getOrElse(throw new Exception("Type of control is not configured with instantiation method."))
            .reduce(_ orElse _)
            .applyOrElse(newControl, throw new Exception("Did not know how to wrap newControl in a Control type.")).asInstanceOf[Control[COut]]
        // TODO: This is probably not correct now that I'm assuming it's the Controller that is implements Control[?], not the parent class of the loaded control.
        // TODO: Should probably ask Claude or something to look at this signature, because I'm pretty sure there's a more "correct" way to do this. I thought it was "def fromFXML[COut : ClassTag](..)", and then I could use classof[COut], but apparently not?
    }
}

/** A parent class for controls based on ScalaFX controls.
  *
  * @param node
  *   The ScalaFX node wrapped by this object.
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
    InnerNode <: scalafx.scene.Node
](val node: InnerNode)(using
    inConversion: Conversion[COut, CIn],
    outConversion: Conversion[CIn, COut]
) extends ControlBase(using inConversion, outConversion)
