package jackflashtech.test.bracelet

import fxmonad.sfx.TextFieldControl
import scalafx.beans.property.ObjectProperty
import org.hid4java.HidManager
import scala.jdk.CollectionConverters._
import scala.util.Try

import IntensityInstances.given

/** A placeholder [[fxmonad.Control]]`[Intensity]` representing the physical
  * "Bracelet" HID device.
  *
  * This is intentionally not wired up to real hardware yet: it looks up the
  * device by product name (if attached) but does not poll it. Once implemented,
  * holding a button on the device should ramp `this()` up towards
  * `Intensity.max` while held, and stop ramping (holding the last value) on
  * release.
  */
class BraceletHidControl(deviceName: String = "Bracelet")
    extends TextFieldControl[Intensity](
      ObjectProperty[Intensity](Intensity.min)
    ) {

  private val device = Try {
    HidManager
      .getHidServices()
      .getAttachedHidDevices()
      .asScala
      .find(_.getProduct() == deviceName)
  }.toOption.flatten

  // TODO: Not implemented. Once a real device is available: open `device`,
  //  poll its input reports for the "held" button state on a background
  //  thread, and while held call `this() = Intensity.clamped(this().value + step)`
  //  until `Intensity.max` is reached, stopping the ramp on release.
}
