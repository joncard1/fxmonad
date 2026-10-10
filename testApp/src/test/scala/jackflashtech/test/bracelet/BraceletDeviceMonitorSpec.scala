package jackflashtech.test.bracelet

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._
import jackflashtech.test.bracelet.BraceletHidControl.ButtonNumber

/** Exercises [[BraceletDeviceMonitor]] and [[BraceletHidControl]] without HID
  * hardware, using fake devices that mimic hid4java's locking: data is
  * delivered while holding the device's monitor, and `close()` takes it too.
  */
class BraceletDeviceMonitorSpec extends munit.FunSuite {

  /** Mimics a hid4java `HidDevice`: equal by path, `close()` synchronized on
    * the device, and data delivered from inside a device-synchronized block.
    */
  final class FakeDevice(
      val path: String,
      initiallyOpen: Boolean = false,
      openSucceeds: Boolean = true
  ) extends BraceletDevice {
    @volatile private var closed = !initiallyOpen
    val opens = new AtomicInteger(0)
    val closes = new AtomicInteger(0)

    override def product: String = "Bracelet"
    override def isClosed: Boolean = closed
    override def open(): Boolean = synchronized {
      opens.incrementAndGet(): Unit
      if (openSucceeds) closed = false
      openSucceeds
    }
    override def close(): Unit = synchronized {
      closes.incrementAndGet(): Unit
      closed = true
    }

    /** Delivers a report the way hid4java's data-read thread does. */
    def deliver(monitor: BraceletDeviceMonitor, data: Array[Byte]): Unit =
      synchronized { monitor.dataReceived(this, data) }

    override def equals(other: Any): Boolean = other match {
      case d: FakeDevice => d.path == path
      case _             => false
    }
    override def hashCode(): Int = path.hashCode()
  }

  /** Holds tasks until `runAll()`, standing in for a not-yet-drained FX queue.
    */
  final class QueueExecutor extends Executor {
    private val tasks = new ConcurrentLinkedQueue[Runnable]()
    override def execute(task: Runnable): Unit = tasks.add(task): Unit
    def pending: Int = tasks.size()
    def runAll(): Unit =
      Iterator.continually(tasks.poll()).takeWhile(_ != null).foreach(_.run())
  }

  private def press(buttons: Int*): Array[Byte] =
    Array(buttons.map(1 << _).foldLeft(0)(_ | _).toByte)

  private def newControl(
      monitor: BraceletDeviceMonitor,
      publishOn: Executor,
      increment: ButtonNumber.ButtonNumber = ButtonNumber(0),
      decrement: ButtonNumber.ButtonNumber = ButtonNumber(1)
  ): BraceletHidControl = {
    val control =
      new BraceletHidControl(monitor, increment, decrement, publishOn)
    monitor.register(control)
    control
  }

  private def connectedAndMounted(
      publishOn: Executor
  ): (BraceletDeviceMonitor, FakeDevice, BraceletHidControl) = {
    val monitor = new BraceletDeviceMonitor()
    val device = new FakeDevice("dev-1")
    val control = newControl(monitor, publishOn)
    monitor.deviceAttached(device)
    control.mountControl(None)
    (monitor, device, control)
  }

  test("presses are applied on the publishing executor, not the HID thread") {
    val publish = new QueueExecutor()
    val (monitor, device, control) = connectedAndMounted(publish)

    device.deliver(monitor, press(0))
    assertEquals(control().asInt, 0, "applied before the executor ran")

    publish.runAll()
    assertEquals(control().asInt, 1)
  }

  test("increment and decrement buttons step the value both ways") {
    val publish = new QueueExecutor()
    val (monitor, device, control) = connectedAndMounted(publish)

    (1 to 3).foreach(_ => device.deliver(monitor, press(0)))
    device.deliver(monitor, press(1))
    publish.runAll()
    assertEquals(control().asInt, 2)
  }

  test("each control only receives its own buttons") {
    val publish = new QueueExecutor()
    val monitor = new BraceletDeviceMonitor()
    val device = new FakeDevice("dev-1")
    val first = newControl(monitor, publish, ButtonNumber(0), ButtonNumber(1))
    val second = newControl(monitor, publish, ButtonNumber(3), ButtonNumber(4))
    monitor.deviceAttached(device)
    first.mountControl(None)
    second.mountControl(None)

    device.deliver(monitor, press(3))
    publish.runAll()
    assertEquals(first().asInt, 0)
    assertEquals(second().asInt, 1)
  }

  test("reports are ignored while the control is unmounted") {
    val publish = new QueueExecutor()
    val monitor = new BraceletDeviceMonitor()
    val device = new FakeDevice("dev-1")
    val control = newControl(monitor, publish)
    monitor.deviceAttached(device)

    device.deliver(monitor, press(0))
    assertEquals(publish.pending, 0)
    publish.runAll()
    assertEquals(control().asInt, 0)
  }

  test("unmounting one control doesn't stop another on the same device") {
    val publish = new QueueExecutor()
    val monitor = new BraceletDeviceMonitor()
    val device = new FakeDevice("dev-1")
    val first = newControl(monitor, publish, ButtonNumber(0), ButtonNumber(1))
    val second = newControl(monitor, publish, ButtonNumber(3), ButtonNumber(4))
    monitor.deviceAttached(device)
    first.mountControl(None)
    second.mountControl(None)
    first.unmountControl(): Unit

    device.deliver(monitor, press(0, 3))
    publish.runAll()
    assertEquals(first().asInt, 0)
    assertEquals(second().asInt, 1)
  }

  test("mounting with no device attached does not enable updates") {
    val publish = new QueueExecutor()
    val monitor = new BraceletDeviceMonitor()
    val device = new FakeDevice("dev-1", initiallyOpen = true)
    val control = newControl(monitor, publish)
    control.mountControl(None)

    device.deliver(monitor, press(0))
    publish.runAll()
    assertEquals(control().asInt, 0)
    assert(!monitor.isConnected)

    monitor.deviceAttached(device)
    device.deliver(monitor, press(0))
    publish.runAll()
    assertEquals(control().asInt, 1)
  }

  test("a press queued before a detach is discarded") {
    val publish = new QueueExecutor()
    val (monitor, device, control) = connectedAndMounted(publish)

    device.deliver(monitor, press(0))
    monitor.deviceDetached(new FakeDevice("dev-1"))
    publish.runAll()

    assertEquals(control().asInt, 0)
    assert(!monitor.isConnected)
    assertEquals(device.closes.get(), 1, "the opened instance is closed")
  }

  test("a press queued before an unmount is discarded, even after remounting") {
    val publish = new QueueExecutor()
    val (monitor, device, control) = connectedAndMounted(publish)

    device.deliver(monitor, press(0))
    control.unmountControl(): Unit
    control.mountControl(None)
    publish.runAll()

    assertEquals(control().asInt, 0)
  }

  test("a failure disconnects the device like a detach") {
    val publish = new QueueExecutor()
    val (monitor, device, control) = connectedAndMounted(publish)

    monitor.deviceFailed(device)
    device.deliver(monitor, press(0))
    publish.runAll()

    assertEquals(control().asInt, 0)
    assert(!monitor.isConnected)
    assertEquals(device.closes.get(), 1)
  }

  test("detaching a different device leaves the current one connected") {
    val publish = new QueueExecutor()
    val (monitor, device, control) = connectedAndMounted(publish)

    monitor.deviceDetached(new FakeDevice("dev-2"))
    device.deliver(monitor, press(0))
    publish.runAll()

    assertEquals(control().asInt, 1)
    assertEquals(device.closes.get(), 0)
  }

  test("re-attaching the current device does not open it again") {
    val publish = new QueueExecutor()
    val (monitor, device, control) = connectedAndMounted(publish)
    val sameDeviceOtherInstance = new FakeDevice("dev-1")

    monitor.deviceAttached(device)
    monitor.deviceAttached(sameDeviceOtherInstance)
    assertEquals(device.opens.get(), 1)
    assertEquals(sameDeviceOtherInstance.opens.get(), 0)

    device.deliver(monitor, press(0))
    publish.runAll()
    assertEquals(control().asInt, 1)
  }

  test("an already-open device is used without reopening it") {
    val monitor = new BraceletDeviceMonitor()
    val device = new FakeDevice("dev-1", initiallyOpen = true)
    monitor.deviceAttached(device)
    assert(monitor.isConnected)
    assertEquals(device.opens.get(), 0)
  }

  test("a device that fails to open leaves the monitor disconnected") {
    val monitor = new BraceletDeviceMonitor()
    monitor.deviceAttached(new FakeDevice("dev-1", openSucceeds = false))
    assert(!monitor.isConnected)
  }

  test("empty reports are ignored") {
    val publish = new QueueExecutor()
    val (monitor, device, _) = connectedAndMounted(publish)
    device.deliver(monitor, Array.emptyByteArray)
    assertEquals(publish.pending, 0)
  }

  test("stop discards queued presses, closes the device, and ignores events") {
    val publish = new QueueExecutor()
    val (monitor, device, control) = connectedAndMounted(publish)

    device.deliver(monitor, press(0))
    monitor.stop()
    publish.runAll()
    assertEquals(control().asInt, 0)
    assertEquals(device.closes.get(), 1)

    val later = new FakeDevice("dev-1")
    monitor.deviceAttached(later)
    later.deliver(monitor, press(0))
    publish.runAll()
    assert(!monitor.isConnected)
    assertEquals(later.opens.get(), 0)
    assertEquals(control().asInt, 0)
  }

  // --- Multi-threaded scenarios -------------------------------------------

  private def withPublishThread[A](
      body: (ExecutorService, () => Thread) => A
  ): A = {
    @volatile var thread: Thread = null
    val executor = Executors.newSingleThreadExecutor(r => {
      val t = new Thread(r, "publish")
      t.setDaemon(true)
      thread = t
      t
    })
    try body(executor, () => thread)
    finally executor.shutdownNow(): Unit
  }

  /** Waits until everything submitted to `executor` so far has run. */
  private def drain(executor: Executor): Unit = {
    val latch = new CountDownLatch(1)
    executor.execute(() => latch.countDown())
    assert(latch.await(10, TimeUnit.SECONDS), "publish executor never drained")
  }

  private def runThreads(threads: Seq[Thread]): Unit = {
    threads.foreach(_.start())
    threads.foreach(_.join(10000))
    assert(
      threads.forall(!_.isAlive()),
      "threads did not finish (deadlock?)"
    )
  }

  test("concurrent reports are each applied exactly once") {
    withPublishThread { (publish, _) =>
      val (monitor, device, control) = connectedAndMounted(publish)
      val threads = (1 to 4).map(_ =>
        new Thread(() =>
          (1 to 20).foreach(_ => device.deliver(monitor, press(0)))
        )
      )
      runThreads(threads)
      drain(publish)
      assertEquals(control().asInt, 80)
    }
  }

  test("detach does not deadlock with a data callback holding the device") {
    val publish = new QueueExecutor()
    val (monitor, device, _) = connectedAndMounted(publish)
    val deviceLocked = new CountDownLatch(1)

    val dataThread = new Thread(() =>
      device.synchronized {
        deviceLocked.countDown()
        // Give the detach a chance to start (and, if it held the monitor's
        // lock while closing, to deadlock with this callback).
        Thread.sleep(100)
        monitor.dataReceived(device, press(0))
      }
    )
    val detachThread = new Thread(() => {
      deviceLocked.await()
      monitor.deviceDetached(device)
    })
    runThreads(List(dataThread, detachThread))
    assertEquals(device.closes.get(), 1)
  }

  test(
    "lifecycle churn concurrent with reports only touches the value on the publish thread"
  ) {
    withPublishThread { (publish, publishThread) =>
      val monitor = new BraceletDeviceMonitor()
      val device = new FakeDevice("dev-1")
      val control = newControl(monitor, publish)
      val writers = new ConcurrentLinkedQueue[Thread]()
      val errors = new ConcurrentLinkedQueue[Throwable]()
      control.defaultProperty.onChange((_, _, _) =>
        writers.add(Thread.currentThread()): Unit
      ): Unit

      val running = new AtomicBoolean(true)
      def loop(body: => Unit): Thread = new Thread(() =>
        try { while (running.get()) body }
        catch { case t: Throwable => errors.add(t): Unit }
      )
      val data = loop(device.deliver(monitor, press(0)))
      val dataDown = loop(device.deliver(monitor, press(1)))
      val scanner = loop {
        monitor.deviceAttached(new FakeDevice("dev-1"))
        monitor.deviceDetached(device)
      }
      val app = loop {
        publish.execute(() => control.mountControl(None))
        publish.execute(() => control.unmountControl(): Unit)
        Thread.sleep(1)
      }
      val threads = List(data, dataDown, scanner, app)
      threads.foreach(_.start())
      Thread.sleep(500)
      running.set(false)
      threads.foreach(_.join(10000))
      assert(threads.forall(!_.isAlive()), "threads did not finish (deadlock?)")
      drain(publish)

      assert(errors.isEmpty, errors.asScala.mkString("\n"))
      assert(
        writers.asScala.forall(_ eq publishThread()),
        s"value written on: ${writers.asScala.map(_.getName()).toSet.mkString(", ")}"
      )

      // Once things settle, the control works normally.
      monitor.deviceAttached(device)
      publish.execute(() => control.mountControl(None))
      drain(publish)
      val before = control().asInt
      device.deliver(monitor, if (before < 100) press(0) else press(1))
      drain(publish)
      assertEquals(
        control().asInt,
        if (before < 100) before + 1 else before - 1
      )
    }
  }
}
