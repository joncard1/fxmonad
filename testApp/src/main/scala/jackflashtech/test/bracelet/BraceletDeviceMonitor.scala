package jackflashtech.test.bracelet

import org.hid4java.HidDevice
import jackflashtech.test.bracelet.BraceletHidControl.ButtonNumber
import jackflashtech.test.bracelet.BraceletHidControl.ButtonNumber._

/** The minimal view of an HID device that [[BraceletDeviceMonitor]] needs, so
  * the monitor's lifecycle logic can be exercised without hardware.
  *
  * Equality identifies the physical device: two instances describing the same
  * device must be equal (hid4java's `HidDevice` compares by device path).
  */
trait BraceletDevice {
  def product: String
  def isClosed: Boolean
  def open(): Boolean
  def close(): Unit
}

/** Adapts a hid4java `HidDevice`. Case-class equality delegates to
  * `HidDevice.equals`, i.e. the device path.
  */
final case class HidBraceletDevice(device: HidDevice) extends BraceletDevice {
  override def product: String = device.getProduct()
  override def isClosed: Boolean = device.isClosed()
  override def open(): Boolean = device.open()
  override def close(): Unit = device.close()
}

/** Tracks the connection to one named bracelet device and routes its reports to
  * the [[BraceletHidControl]]s registered against its buttons.
  *
  * ==Threading==
  * hid4java calls into this class from several threads:
  *   - device attach/detach/failure events arrive on its device scanner thread,
  *     except for the very first scan, which runs on whichever thread created
  *     the `HidServices` instance;
  *   - each opened device has its own data-read thread, which delivers
  *     [[dataReceived]];
  *   - [[register]], [[mount]], [[unmount]] and [[stop]] come from the
  *     application (normally the JavaFX Application Thread).
  *
  * All mutable state is guarded by this object's monitor, so lifecycle
  * transitions and report handling are serialized. Two rules keep this
  * deadlock-free and side-effect-safe:
  *   1. Device I/O (`open`/`close`) is never performed while holding the lock.
  *      hid4java delivers data from inside a method synchronized on the device,
  *      and `HidDevice.close()` waits on that same lock, so closing a device
  *      while holding a lock the data callback also needs would deadlock.
  *   2. Value updates are never applied on the calling thread. A report is
  *      turned into button presses under the lock, tagged with the current
  *      `generation`, and handed to each control's publishing executor. When
  *      the task runs, it is dropped unless the generation is still current.
  *      Every lifecycle change (attach, detach, failure, mount, unmount, stop)
  *      bumps the generation, so presses queued before e.g. a detach or an
  *      unmount are discarded rather than applied late.
  */
final class BraceletDeviceMonitor {

  // Guarded by `this`.
  private var device: Option[BraceletDevice] = None
  private var generation: Long = 0L
  private var stopped: Boolean = false
  private var buttons: Map[ButtonNumber, BraceletHidControl] = Map()
  private var mounted: Set[BraceletHidControl] = Set()

  /** Routes `control`'s buttons to it. A later registration of the same button
    * replaces an earlier one. Controls start unmounted.
    */
  def register(control: BraceletHidControl): Unit = synchronized {
    buttons = buttons ++ control.buttons.map(_ -> control)
  }

  /** Starts delivering presses to `control` (while a device is connected). */
  def mount(control: BraceletHidControl): Unit = synchronized {
    mounted = mounted + control
    generation += 1
  }

  /** Stops delivering presses to `control`, discarding any still queued. */
  def unmount(control: BraceletHidControl): Unit = synchronized {
    mounted = mounted - control
    generation += 1
  }

  /** Whether a device is currently attached and open. */
  def isConnected: Boolean = synchronized { device.isDefined }

  /** Whether a press tagged with `token` may still be applied to `control`. */
  private[bracelet] def isCurrent(
      control: BraceletHidControl,
      token: Long
  ): Boolean = synchronized {
    !stopped && token == generation && mounted.contains(control)
  }

  /** Called when a device with the monitored product name appears. Opens it if
    * necessary and makes it the current device. Repeated attach events for the
    * current device (hid4java reports already-present devices on its first
    * scan, and [[BraceletHidControl]] also looks for them at start-up) are
    * no-ops, so the device is never opened twice.
    */
  def deviceAttached(candidate: BraceletDevice): Unit = {
    val alreadyCurrent = synchronized { stopped || device.contains(candidate) }
    if (!alreadyCurrent) {
      val wasClosed = candidate.isClosed
      val isOpen = if (wasClosed) candidate.open() else true
      val toClose: Option[BraceletDevice] = synchronized {
        if (!isOpen) {
          println(s"${candidate.product} did not open")
          None
        } else if (stopped || device.contains(candidate)) {
          // Lost a race with stop() or with another attach of the same device.
          // Only close what this call opened, and never the current instance.
          if (wasClosed && !device.exists(_ eq candidate)) Some(candidate)
          else None
        } else {
          val previous = device
          device = Some(candidate)
          generation += 1
          previous
        }
      }
      toClose.foreach(_.close())
    }
  }

  /** Called when a device with the monitored product name disappears. */
  def deviceDetached(candidate: BraceletDevice): Unit =
    disconnect(candidate)

  /** Called when hid4java reports a failure on the device. */
  def deviceFailed(candidate: BraceletDevice): Unit =
    disconnect(candidate)

  private def disconnect(candidate: BraceletDevice): Unit = {
    val removed = synchronized {
      device.filter(_ == candidate) match {
        case some @ Some(_) =>
          device = None
          generation += 1
          some
        case None => None
      }
    }
    // Close the instance this monitor opened (not necessarily `candidate`),
    // which also stops its data-read thread.
    removed.foreach(_.close())
  }

  /** Called on the device's data-read thread with a raw report. Each set bit is
    * a held button; every mounted control registered for a held button gets one
    * press, published through its own executor.
    */
  def dataReceived(source: BraceletDevice, data: Array[Byte]): Unit = {
    if (data.length == 0) {
      println("Bracelet report length should be at least 1 byte.")
    } else {
      // If the report is longer, then the device has written multiple reports
      // since the last DataReadInterval poll. Only the first byte is used.
      val dataByte = data(0)
      val presses = synchronized {
        if (stopped || !device.contains(source)) Nil
        else
          buttons.toList.collect {
            case (button, control)
                if mounted.contains(control) &&
                  ((dataByte >> button.asInt) & 1) == 1 =>
              (control, button, generation)
          }
      }
      presses.foreach((control, button, token) =>
        control.publishButtonPress(button, token)
      )
    }
  }

  /** Permanently stops the monitor: no further presses are delivered, queued
    * ones are discarded, and the current device is closed.
    */
  def stop(): Unit = {
    val toClose = synchronized {
      stopped = true
      generation += 1
      val current = device
      device = None
      current
    }
    toClose.foreach(_.close())
  }
}
