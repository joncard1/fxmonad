package jackflashtech.test.bracelet

import scalafx.beans.property.ObjectProperty
import org.hid4java.HidManager
import org.hid4java.HidServicesListener
import scala.jdk.CollectionConverters._
import scala.util.Try

import fxmonad.Control
import scalafx.beans.property.Property
import org.hid4java.event.HidServicesEvent
import scala.util.Failure
import scala.util.Success
import org.hid4java.HidServicesSpecification
import org.hid4java.HidDevice
import fxmonad.Control.MountContext
import jackflashtech.test.bracelet.BraceletHidControl.startListener
import jackflashtech.test.bracelet.BraceletHidControl.stopListener
import java.util.concurrent.atomic.AtomicReference

/** This assumes there is only one device of name "Bracelet", and it is the
  * correct device. This should support multiple BraceletHidControl objects
  * operating on different subsets of the bracelet sensors, but not multiple
  * devices.
  */
object BraceletHidControl {

  private val DataReadInterval = 200

  /** The interval that the BracletHidControl will increment its internal
    * property for each report, which should be generated every
    * [[DataReadInterval]] milliseconds when the button is held down.
    */
  val StepSize: Int = 1

  def stop(): Unit = {
    services.map(services => {
      listeners.getAndUpdate(listeners => {
        listeners.foreachEntry((_, listener) => {
          services.removeHidServicesListener(listener)
        })
        Map()
      })
    }): Unit
  }

  // TODO: Figure out if this should be in an AtomicReference. It will be accessed from at least two threads (JavaFX Application Thread and the java4hid thread, at least).
  private val listeners: AtomicReference[Map[String, BraceletServiceListener]] =
    AtomicReference(Map())

  def startListener(deviceName: String): Unit = {
    listeners.get
      .get(deviceName)
      .map(listener => {
        listener.state.getAndUpdate(_ => listener.ActiveState())
      }): Unit
  }
  def stopListener(deviceName: String): Unit = {
    listeners.get
      .get(deviceName)
      .map(listener => {
        listener.state.getAndUpdate(_ => listener.InactiveState())
      }): Unit
  }

  class BraceletServiceListener(deviceName: String)
      extends HidServicesListener {

    var state: AtomicReference[BraceletHidState] = AtomicReference(
      InactiveState()
    )

    var controls: AtomicReference[Map[Int, BraceletHidControl]] =
      AtomicReference(Map())

    trait BraceletHidState {
      // To truly implement the state machine pattern, this should return an instance of BraceletHidState, but in this case the states never transition to new states, so it's not worth it.
      def processData(data: Array[Byte]): BraceletHidState
    }

    case class ActiveState() extends BraceletHidState {
      override def processData(data: Array[Byte]): BraceletHidState = {
        if (data.length == 0) {
          println("Bracelet report length should be at least 1 byte.")
          return this
        }
        // If the report is longer, then the device has written multiple reports since the last DataReadInterval poll. I believe it to be a good assumption that the last byte is the newest data, given what I have seen of hid4java's use ObjectStream to read data from the device, but that assumption underlies this algorithm.
        val dataByte = data(data.length - 1)
        val array = (0 to 7).map { i =>
          ((dataByte >> i) & 1) == 1
        }.toArray
        controls.getAndUpdate(cntrls => {
          for (buttonNumber <- cntrls.keySet) {
            if ((buttonNumber < 0) || (buttonNumber >= array.length)) {
              println(
                s"A button number $buttonNumber was registerd with the HID controller; this is not compatible with the hardware device."
              )
            } else {
              val buttonPressed = array(buttonNumber)
              if (buttonPressed) {
                cntrls
                  .get(buttonNumber)
                  .map(control =>
                    control.reportButtonPress(buttonNumber, buttonPressed)
                  ): Unit
              }
            }
          }
          cntrls
        })
        println(s"Length: ${data.length}")
        println(f"Data: ${Integer.toBinaryString(dataByte)}")
        this
      }
    }

    case class InactiveState() extends BraceletHidState {
      override def processData(data: Array[Byte]): BraceletHidState = this
    }

    override def hidDataReceived(event: HidServicesEvent): Unit = {
      if (event.getHidDevice().getProduct().equals(deviceName)) {
        state.getAndUpdate(_.processData(event.getDataReceived())): Unit
      }
    }

    override def hidFailure(event: HidServicesEvent): Unit = {
      if (event.getHidDevice().getProduct().equals(deviceName)) {
        state.set(InactiveState())
      }
    }

    // TODO: I'm not convinced that either attaching or detaching is being done entirely correctly. Probably there are threading issues with setting the state, or something else.
    override def hidDeviceDetached(event: HidServicesEvent): Unit = {
      if (event.getHidDevice().getProduct().equals(deviceName)) {
        state.set(InactiveState())
      }
    }

    override def hidDeviceAttached(event: HidServicesEvent): Unit = {
      if (event.getHidDevice().getProduct().equals(deviceName)) {
        val isOpen = if (event.getHidDevice().isClosed()) {
          openDevice(event.getHidDevice())
        } else {
          true
        }
        if (isOpen) {
          state.set(ActiveState())
        } else {
          state.set(InactiveState())
        }
      }
    }
  }

  private val specification = new HidServicesSpecification()
  specification.setAutoDataRead(true)
  specification.setDataReadInterval(DataReadInterval)

  lazy private val services = Try {
    HidManager
      .getHidServices(specification)
  } match {
    case Failure(exception) =>
      println(s"Exception getting HID services. ${exception.toString()}")
      // TODO: Possibly should throw this.
      None
    case Success(services) =>
      services.start()
      Some(services)
  }

  def apply[A](
      deviceName: String,
      incrementButton: Int,
      decrementButton: Int
  ): BraceletHidControl = {

    def registerButtonNumber(
        listener: BraceletServiceListener,
        buttonNumber: Int,
        control: BraceletHidControl
    ): Unit = {
      if ((buttonNumber < 0) || (buttonNumber > 7))
        throw Exception(
          "Only buttons with numbers 0-7 inclusive can be registered for this device."
        )
      listener.controls.getAndUpdate(controls =>
        controls + (buttonNumber -> control)
      ): Unit
    }

    if (services.isEmpty)
      throw new Exception("Failed to find an HidServices object.")
    val control = services.flatMap(services => {
      val incButton = incrementButton
      val decButton = decrementButton
      val devName = deviceName
      val control = new BraceletHidControl {
        protected val incrementButton = incButton;
        protected val decrementButton = decButton;
        protected val deviceName = devName
      }

      listeners
        .getAndUpdate(listeners =>
          listeners + (deviceName -> listeners
            .get(deviceName)
            .fold({
              val listener = new BraceletServiceListener(deviceName)
              services
                .getAttachedHidDevices()
                .asScala
                .find(_.getProduct().equals(deviceName))
                .fold({
                  registerButtonNumber(listener, incrementButton, control)
                  registerButtonNumber(listener, decrementButton, control)
                  Some(control)
                })(device => {
                  val isOpen = openDevice(device)

                  if (isOpen) {
                    registerButtonNumber(listener, incrementButton, control)
                    registerButtonNumber(listener, decrementButton, control)
                    listener.state.set(listener.ActiveState())
                    Some(control)
                  } else {
                    throw new Exception(s"Device ${deviceName} did not open")
                  }
                }): Unit
              services.addHidServicesListener(listener)
              listener
            })(listener => {
              registerButtonNumber(listener, incrementButton, control)
              registerButtonNumber(listener, decrementButton, control)
              listener
            }))
        )
      Option(control)
    })

    control.get
  }

  private def openDevice(device: HidDevice) = {
    val isOpen = if (device.isClosed()) {
      println("Am trying to open")
      device.open()
    } else {
      println("Was opened when I found it")
      true
    }
    println(s"${device.getProduct()} is ${isOpen}")
    isOpen
  }
}

/** A placeholder [[fxmonad.Control]]`[Intensity]` representing the physical
  * "Bracelet" HID device.
  */
// TODO: Interesting question: when it's dismounted, stop listening to the HID data? This suggests that controls may need to handle their own dismounting, which may "solve" the fact that ControlContainer objects need to know a lot about JavaFX, etc., which it would be better if it didn't.
// TODO: Does this impact the ControlPane idea? Being able to dismount themselves doesn't imply knowing enough context to mount themselves, especially when they may be re-mounting themselves into a new location.
trait BraceletHidControl extends Control[Intensity] {
  protected val deviceName: String
  protected val incrementButton: Int
  protected val decrementButton: Int

  override def showError(errorMsg: String): Unit = {
    println("Bracelet HID was instructed to show an error.")
  }

  override def clearError(): Unit = {
    println("Bracelet HID was instructed to clear an error.")
  }

  override val defaultProperty: Property[Intensity, ?] =
    ObjectProperty[Intensity](Intensity.min)

  def reportButtonPress(buttonNumber: Int, pressed: Boolean) = {
    if ((buttonNumber == incrementButton) && pressed) {
      defaultProperty() =
        Intensity.clamped(defaultProperty().value + BraceletHidControl.StepSize)
    } else if ((buttonNumber == decrementButton) && pressed) {
      defaultProperty() =
        Intensity.clamped(defaultProperty().value - BraceletHidControl.StepSize)
    }
  }

  // TODO: These should take no context and should return no context. They just need to start and stop their listeners.
  override def mountControl(context: Option[MountContext]): Unit =
    startListener(deviceName)
  override def unmountControl(): Option[MountContext] = {
    stopListener(deviceName)
    None
  }
}
