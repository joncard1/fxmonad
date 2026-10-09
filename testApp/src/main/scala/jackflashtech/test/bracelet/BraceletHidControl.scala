package jackflashtech.test.bracelet

import scalafx.beans.property.ObjectProperty
import org.hid4java.HidManager
import org.hid4java.HidServices
import org.hid4java.HidServicesListener
import scala.jdk.CollectionConverters._
import scala.util.Try

import fxmonad.Control
import scalafx.beans.property.Property
import org.hid4java.event.HidServicesEvent
import scala.util.Failure
import scala.util.Success
import org.hid4java.HidServicesSpecification
import fxmonad.Control.MountContext
import java.util.concurrent.Executor
import jackflashtech.test.bracelet.Intensities._

/** This assumes there is only one device of name "Bracelet", and it is the
  * correct device. This should support multiple BraceletHidControl objects
  * operating on different subsets of the bracelet sensors, but not multiple
  * devices.
  */
object BraceletHidControl {
  import ButtonNumber._

  object ButtonNumber {
    import scala.compiletime.error

    opaque type ButtonNumber = Int

    inline def apply(inline n: Int): ButtonNumber = inline if (
      (n < 0) || (n > 7)
    ) error("Button numbers must be between 0 and 7")
    else n

    extension (n: ButtonNumber) def asInt: Int = n

    extension (n: Int)
      def asButtonNumber: Either[String, ButtonNumber] = if ((n < 0) || (n > 7))
        Left("Button numbers must be between 0 and 7")
      else Right(n)
  }

  private val DataReadInterval = 200

  /** The interval that the BracletHidControl will increment its internal
    * property for each report, which should be generated every
    * [[DataReadInterval]] milliseconds when the button is held down.
    */
  val StepSize: Intensity = Intensity(IntensityRange(1))

  /** Forwards hid4java events for one product name to its monitor. */
  private final class BraceletServiceListener(
      deviceName: String,
      monitor: BraceletDeviceMonitor
  ) extends HidServicesListener {
    private def forThisDevice(event: HidServicesEvent)(
        f: BraceletDevice => Unit
    ): Unit =
      Option(event.getHidDevice())
        .filter(device => deviceName == device.getProduct())
        .foreach(device => f(HidBraceletDevice(device)))

    override def hidDeviceAttached(event: HidServicesEvent): Unit =
      forThisDevice(event)(monitor.deviceAttached)
    override def hidDeviceDetached(event: HidServicesEvent): Unit =
      forThisDevice(event)(monitor.deviceDetached)
    override def hidFailure(event: HidServicesEvent): Unit =
      forThisDevice(event)(monitor.deviceFailed)
    override def hidDataReceived(event: HidServicesEvent): Unit =
      forThisDevice(event)(monitor.dataReceived(_, event.getDataReceived()))
  }

  private val specification = new HidServicesSpecification()
  specification.setAutoDataRead(true)
  specification.setDataReadInterval(DataReadInterval)

  // Guarded by `this`. Only touched by apply() and stop(), never by hid4java
  // callbacks, which go straight to the per-device monitors.
  private var services: Option[HidServices] = None
  private var monitors
      : Map[String, (BraceletDeviceMonitor, BraceletServiceListener)] = Map()

  private def hidServices(): HidServices = synchronized {
    services.getOrElse {
      Try(HidManager.getHidServices(specification)) match {
        case Failure(exception) =>
          throw new Exception(
            "Failed to find an HidServices object.",
            exception
          )
        case Success(created) =>
          created.start()
          services = Some(created)
          created
      }
    }
  }

  /** Returns the monitor for `deviceName`, creating it on first use. A new
    * monitor's listener is registered *before* looking for an already-attached
    * device, so an attach can't slip between the two; the monitor ignores the
    * duplicate if both see it.
    */
  private def monitorFor(deviceName: String): BraceletDeviceMonitor = {
    val (monitor, created, hid) = synchronized {
      val hid = hidServices()
      monitors.get(deviceName) match {
        case Some((monitor, _)) => (monitor, false, hid)
        case None               =>
          val monitor = new BraceletDeviceMonitor()
          val listener = new BraceletServiceListener(deviceName, monitor)
          hid.addHidServicesListener(listener)
          monitors = monitors + (deviceName -> (monitor, listener))
          (monitor, true, hid)
      }
    }
    if (created) {
      hid
        .getAttachedHidDevices()
        .asScala
        .find(device => deviceName == device.getProduct())
        .foreach(device => monitor.deviceAttached(HidBraceletDevice(device)))
    }
    monitor
  }

  /** Creates a control driven by two buttons of the named device.
    *
    * @param publishOn
    *   the executor on which the control's value is updated, and therefore on
    *   which all of its listeners run. It must be the thread that owns every
    *   control this one feeds into — for ScalaFX-backed controls, the JavaFX
    *   Application Thread (`fxmonad.sfx.FXThreadExecutor`).
    */
  def apply(
      deviceName: String,
      incrementButton: Int,
      decrementButton: Int,
      publishOn: Executor
  ): BraceletHidControl = {
    val incButton = incrementButton.asButtonNumber
      .fold(msg => throw Exception(msg), bn => bn)
    val decButton = decrementButton.asButtonNumber
      .fold(msg => throw Exception(msg), bn => bn)
    val monitor = monitorFor(deviceName)
    val control =
      new BraceletHidControl(monitor, incButton, decButton, publishOn)
    // Registered only once fully constructed, so a data-read thread can never
    // observe a partially-initialized control.
    monitor.register(control)
    control
  }

  /** Shuts down HID handling: in-flight and queued reports are discarded, the
    * devices opened by the monitors are closed, and hid4java's scanner thread
    * is stopped. Controls created earlier stop updating.
    */
  def stop(): Unit = {
    val (toStop, stoppedMonitors) = synchronized {
      val current = (services, monitors.values.toList)
      services = None
      monitors = Map()
      current
    }
    stoppedMonitors.foreach((monitor, _) => monitor.stop())
    toStop.foreach(services => {
      stoppedMonitors.foreach((_, listener) =>
        services.removeHidServicesListener(listener)
      )
      // Stops the scanner thread and closes the device instances hid4java
      // itself tracks. The native library is released by hid4java's own
      // shutdown hook.
      services.stop()
    })
  }
}

/** A [[fxmonad.Control]]`[Intensity]` driven by two buttons of the physical
  * "Bracelet" HID device: holding the increment (decrement) button raises
  * (lowers) the value by [[BraceletHidControl.StepSize]] per report.
  *
  * ==Threading and supported use==
  * The value is *produced* by the device, never consumed. `defaultProperty` is
  * written only on the `publishOn` executor given to
  * [[BraceletHidControl.apply]], so its listeners (binders, containers, and the
  * widgets downstream of them) all run on that executor's thread. hid4java
  * threads never touch the property.
  *
  * Writing to this control's value from elsewhere is unsupported: assigning it
  * (`hid() = x`), binding it as the output of another control (`hid(other) =
  * ...`), or binding another control's property into it. Such writes are not
  * synchronized with the device, will be overwritten by the next press, and, if
  * made from a thread other than `publishOn`'s, are a data race.
  *
  * Presses are only applied while the control is mounted (between
  * [[mountControl]] and [[unmountControl]]) and a device is connected.
  */
// TODO: Does this impact the ControlPane idea? Being able to dismount themselves doesn't imply knowing enough context to mount themselves, especially when they may be re-mounting themselves into a new location.
final class BraceletHidControl private[bracelet] (
    monitor: BraceletDeviceMonitor,
    incrementButton: BraceletHidControl.ButtonNumber.ButtonNumber,
    decrementButton: BraceletHidControl.ButtonNumber.ButtonNumber,
    publishOn: Executor
) extends Control[Intensity] {
  import BraceletHidControl.ButtonNumber.ButtonNumber

  override def showError(errorMsg: String): Unit = {
    println("Bracelet HID was instructed to show an error.")
  }

  override def clearError(): Unit = {
    println("Bracelet HID was instructed to clear an error.")
  }

  override val defaultProperty: Property[Intensity, ?] =
    ObjectProperty[Intensity](Intensity.min)

  private[bracelet] def buttons: List[ButtonNumber] =
    List(incrementButton, decrementButton)

  /** Called by the monitor (on a hid4java thread). Hands the press to
    * `publishOn`, where it is applied only if `token` is still current.
    */
  private[bracelet] def publishButtonPress(
      buttonNumber: ButtonNumber,
      token: Long
  ): Unit =
    publishOn.execute(() =>
      if (monitor.isCurrent(this, token)) applyButtonPress(buttonNumber)
    )

  private def applyButtonPress(buttonNumber: ButtonNumber): Unit = {
    if (buttonNumber == incrementButton) {
      defaultProperty() = defaultProperty() + BraceletHidControl.StepSize
    } else if (buttonNumber == decrementButton) {
      defaultProperty() = defaultProperty() - BraceletHidControl.StepSize
    }
  }

  // TODO: These should take no context and should return no context. They just need to start and stop their listeners.
  // Mounting just starts and stops this control's share of the device's
  // reports; there is no location to remember, so no context is used or
  // returned.
  override def mountControl(context: Option[MountContext]): Unit =
    monitor.mount(this)
  override def unmountControl(): Option[MountContext] = {
    monitor.unmount(this)
    None
  }
}
