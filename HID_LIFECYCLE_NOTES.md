# HID Lifecycle Issues

The following is the assessment of the HID device implementation with respect to thread-safety. These constitute a substantial redesign or at least reconsideration of how the system works, so I am making a note of them here and will add these to the roadmap rather than fix them before committing the substantial changes the addition of the HID device interoperability has necessitated.

## Lifecycle Races Remain

hidDeviceAttached now treats an already-open device as open and sets the listener active (BraceletHidControl.scala:123). That fixes the bug where an attach event left an already-open device inactive.

The AtomicReference does not fully serialize attach/detach against data callbacks, though. A report can be processing while detach sets the state inactive, and its property update can still happen afterward. Also, startListener can set the state active without checking that the device is attached/open (BraceletHidControl.scala:41). So I’d consider the reported reconnect case addressed, but the broader lifecycle behavior remains a risk.


## AtomicReferences help visibility, but don’t make this thread-safe end to end.

The references safely publish the current state and immutable maps, but several getAndUpdate lambdas perform side effects. For example, state.getAndUpdate(_.processData(...)) can re-run processData if there’s contention, duplicating its property updates and logging (BraceletHidControl.scala:104). The controls.getAndUpdate callback also calls reportButtonPress while deriving the map (BraceletHidControl.scala:81). Atomic update functions should be pure because they may be retried.

More importantly, reportButtonPress changes an observable control property on the HID callback thread (BraceletHidControl.scala:254). Property listeners run synchronously: the binder reacts immediately (ControlBinder.scala:37), and a display listener updates its JavaFX label (LabelControl.scala:72). Thus the atomic references do not prevent JavaFX work from being triggered off the FX thread.

Recommendation: keep immutable snapshots in atomic references if useful, but keep their update functions free of side effects. For the current app, a straightforward approach is to serialize HID lifecycle and report handling through one executor or a synchronized state transition, then marshal only the resulting intensity-property update to the JavaFX thread. If reports are queued with Platform.runLater, guard against stale queued updates after detach, for example with a connection generation/token. Also, stop() removes listeners but does not visibly stop the HID services themselves (BraceletHidControl.scala:27); that lifecycle cleanup should be reviewed alongside #5.

## Stop functionality is not properly implemented

BraceletApp now calls BraceletHidControl.stop() on window close, and stop() removes the registered HID services listeners and clears the listener map.

It is not a complete shutdown: the current code does not visibly stop the HID services or close the device, and it doesn’t coordinate shutdown with callbacks already in flight. So the stop hook is useful, but the remaining cleanup belongs in the deferred lifecycle work recorded in HID_LIFECYCLE_NOTES.md.


## Resolution

The issues above were addressed by restructuring rather than by atomics:

- **hid4java's threads.** Attach/detach/failure events arrive on its device scanner thread, except the first scan, which runs on whichever thread creates `HidServices` (the FX thread here). Each opened `HidDevice` has its own data-read thread. `HidDevice.close()` synchronizes on the device, which the data-read thread holds while delivering data.
- **One lock, no side effects under it.** `BraceletDeviceMonitor` holds all connection state behind its own monitor, so attach, detach, failure, data, mount/unmount and stop are serialized. Device `open`/`close` and publishing always happen *outside* that lock, which avoids deadlocking against the data-read thread's device lock. The `AtomicReference`s (and their side-effecting update functions) are gone.
- **Property updates are marshalled by the caller's choice of executor, not by the HID.** `BraceletHidControl` takes a plain `java.util.concurrent.Executor` and only writes its property there. The app passes `fxmonad.sfx.FXThreadExecutor`, so the HID code has no JavaFX dependency while every downstream listener runs on the FX thread.
- **Stale updates.** Every lifecycle change bumps a generation counter. Presses are tagged with it on the data thread and dropped on the executor if it has moved on (e.g. a detach or unmount happened while the press was queued).
- **Connected vs. mounted.** These are separate facts now: presses are delivered only to *mounted* controls *while* a device is attached and open. Mounting no longer marks a disconnected device active, and unmounting one control no longer stops other controls on the same device.
- **Stop.** `BraceletHidControl.stop()` stops the monitors (discarding queued presses and closing the device instances they opened, which hid4java doesn't track), removes the listeners, and stops hid4java's scanner. The native library is still released by hid4java's own shutdown hook.

Writing to a `BraceletHidControl`'s value from outside (assigning it, binding it as an output, or binding another property into it) is documented as unsupported.

Tests: `testApp/src/test/scala/jackflashtech/test/bracelet/BraceletDeviceMonitorSpec.scala`, using fake devices that mimic hid4java's locking.
