# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Built with sbt (Scala 3.8, JVM, ScalaFX/JavaFX 25). All commands run from the repo root. The root project only aggregates `core`, `macros`, and `testApp`.

- `sbt compile` — compile all subprojects.
- `sbt "testApp/runMain jackflashtech.test.MainApp"` — launch the main demo, which loads `main-screen.fxml` and exercises the fxmonad DSL end-to-end. This is the primary way to hand-verify behavior.
- `sbt "testApp/runMain jackflashtech.test.bracelet.BraceletApp"` — launch the "bracelet" demo (custom value type `Intensity`, `ControlPane`, `ToggleGroupControl`, and a non-widget HID-backed control via hid4java). `testApp/run` alone will prompt to choose between the two mains.
- `sbt test` — run the core MUnit test suite (headless; see Testing below).
- `sbt "core/testOnly fxmonad.sfx.TextFieldControlBindingSpec"` — run one test class.
- `sbt testApp/test` — run the bracelet HID lifecycle/threading tests (no hardware or JavaFX needed).
- `sbt scalafmtAll` — format sources (scalafmt 3.10.7, dialect `scala3`; see `.scalafmt.conf`). CI (`.github/workflows/scala.yml`, JDK 25) runs `sbt scalafmtCheckAll compile +test`, so unformatted code fails the build.

Notes:
- Compiler flags `-Yretain-trees` (required so the macro annotations can inspect val definitions) and `-Wall` are set in `build.sbt`. Discarded non-`Unit` values must be made explicit (e.g. `...: Unit`, `private val _ = x.onChange(...)`) to keep `-Wall` quiet.
- Anything using `@FXMonad` / `@FXEmitter` (controllers, `MainApp`, `BraceletApp`) must be marked `@experimental` — `MacroAnnotation` is still an experimental Scala 3 feature.

### Testing

Tests live in `core/src/test/scala/fxmonad/` and use MUnit + ScalaCheck + TestFX with the Monocle headless platform. `core` tests run in a forked JVM with `-Dtestfx.headless=true -Dprism.order=sw -Dprism.text=t2k` (set in `build.sbt`). Specs call `FxToolkit.registerPrimaryStage()` in `beforeEach` / `FxToolkit.cleanupStages()` in `afterEach`, and run anything touching JavaFX inside an `onFx { ... }` helper (a `Platform.runLater` + `CountDownLatch` wrapper) — see `sfx/TextFieldControlBindingSpec.scala` for the pattern. Because much of the library defers work via `Platform.runLater`, assertions generally need a further `onFx` round-trip to observe the effect.

## Architecture

The library builds a monad-like DSL over JavaFX/ScalaFX controls so that a control is typed purely by the *value* it produces or consumes, independent of the underlying widget class. It also has a separate, Elm-like path for non-data events (button clicks).

### The core abstraction: `Control[COut]`

`fxmonad.Control[COut]` (in `core/src/main/scala/fxmonad/Control.scala`) exposes:
- `defaultProperty: Property[COut, ?]` — the observable value of the control.
- `apply()` / `update(newVal)` — read/write the value.
- `update(c1, ..., cN, f)` — bind this control to one, two, or three input controls. `f` returns a new `Control[COut]`; a `ControlBinder{1,2,3}` (`sfx/ControlBinder.scala`) subscribes to the inputs and calls `updateValue()` on change. Re-binding disposes the previous binder.
- `flatMap` — reads the current value and feeds it to `f`.
- `showError` / `clearError` — validation feedback hooks, implemented by each concrete control.
- `mountControl(Option[MountContext])` / `unmountControl(): Option[MountContext]` — attach/detach the control from wherever it lives (for SFX controls, the JavaFX scene graph; see below).
- `isProxy` — true when the control is backed by an `SFXProxy` rather than a live widget.
- `updateFrom` — a `PartialFunction[Control[COut], Unit]` used to absorb another control's state; by default copies the value, overridden by SFX controls to replay proxy changes.

The DSL syntax `c3(c1, c2) = { (a, b) => aControl }` is Scala's `apply`/`update` sugar over these overloads. The function returns *a Control* (not a raw value). `ControlBinder.updateValue` then hands it to `ControlContainer.replaceControl` if the output is a container, otherwise it just copies the value.

### Type-agnostic controls via `Conversion` and `PropertyConstructor`

Every concrete control has an outer value type `COut` (what the user sees) and an inner type `CIn` (what the underlying widget carries — `TextField`/`Label` use `String`, `CheckBox`/`RadioButton` use `Boolean`, `Slider` uses `Double`, `ColorPicker` uses `Color`, `ToggleGroup` uses the selected toggle's `userData: Object`). `ControlBase[COut, CIn]` bridges these.

- `fxmonad.Conversion[T, U]` is a function `T => Either[String, U]` (Left = validation error message). An implicit `castConversion` lifts a plain `scala.Conversion` into it, turning thrown exceptions into `Left`. The standard set (`String↔Int`, `Boolean↔Int`, `Int↔Double`, identities, …) is in `Control`'s companion — bring it into scope with `import fxmonad.Control.given`. To support a new (COut, CIn) pair, provide `given Conversion`s in both directions (see `IntensityInstances` / `BraceletController` companion in the test app).
- `PropertyConstructor[A]` creates an empty `Property` for a value type (`StringProperty`, `IntegerProperty`, …, falling back to `ObjectProperty[A]`). Custom types can supply their own to set a default initial value.

`ControlBase.updateProperty` runs the out-conversion and calls `showError` on failure or a `null` result; each control's `defaultProperty.onChange` listener runs the in-conversion before writing to the widget.

### Concrete controls (`core/src/main/scala/fxmonad/sfx/`)

`SFXControl[COut, CIn, InnerControl]` is the ScalaFX-backed base. It implements `mountControl`/`unmountControl` against `SFXMountContext(parent, index)` (insert/remove in a `Pane`'s children via `Platform.runLater`, never mounting a proxy) and `isProxy`. `TooltipValidationErrorStrategy` is a mixin implementing `showError`/`clearError` as a tooltip plus an `"error"` CSS class.

Widget controls are generic in `COut`: `TextFieldControl`, `CheckBoxControl`, `RadioButtonControl`, `SliderControl`, `LabelControl` (read-only; no-op error display), `ColorPickerControl` (plus the legacy `ColorPickerControlColor`, flagged for removal). Each companion has four `apply` overloads — `()`, `(initialValue)`, `(widget)`, `(initialValue, widget)` — taking `PropertyConstructor` and both `Conversion`s implicitly. Omitting the widget uses the matching proxy. Each class defines listeners in both directions (widget → property via `updateProperty`, property → widget via `defaultProperty.onChange`) and overrides `updateFrom` to replay a same-class proxy's changes.

`ToggleGroupControl` extends `ControlBase` directly (a `ToggleGroup` is not a `Control`/`Node`), maps the selected toggle's `userData` to the value, and leaves `mountControl`/`unmountControl` as `???`.

### Registering and looking up controls

`Control.lookups` (private, in `Control.scala`) maps a value `Class[?]` to a list of `PartialFunction[Object, Control[?]]` constructors. Built-in entries cover `String`, `Int`, `Boolean`, `Double`, and `Color` across the widgets above, wrapping results in a `ControlContainer`, plus `ControlPane[T]` (which initializes itself and returns itself).

- `Control.registerControl(classOf[T], { case w: SomeWidget => ... })` prepends constructors for a type — call before FXML loads (see `BraceletApp.start`). Custom types that may appear inside a `ControlPane` must also register a `case c: ControlPane[T] => c.initializeContainer; c` clause.
- `Control.lookupControl[T](node: javafx.scene.Node)(using ClassTag[T])` returns the first matching constructor's result, or throws.

### The two-layer control model: `ControlContainer`

Bindings can *replace* the underlying control at runtime (e.g. `Controller.initialize` returns a `LabelControl` when the temperature > 15 and a `TextFieldControl` otherwise; `BraceletController` swaps between an on-screen slider and an HID device). `ControlContainer[COut]` (`ControlContainer.scala`) is the stable outer handle user code retains; it wraps a swappable `wrappedControl` and keeps its own `defaultProperty` in sync with it (`setWrappedControl` re-subscribes on swap). `ControlContainer(property, control)` / `ControlContainer(control)` build the default implementation.

`replaceControl(newControl)` reconciles a binding's result:
- If `newControl.isProxy`, the wrapped control absorbs it via `updateFrom` (proxy changes replayed onto the live widget, or just the value) and is **not** swapped.
- Otherwise the wrapped control is unmounted, the new one is mounted using the returned `MountContext`, and it becomes the wrapped control.

Implementations:
- `SFXControlContainer` — the default. Mounts the new control where the old one was; remembers `previousContext` so a widget replaced by a non-widget control (e.g. HID) can be remounted later. (A TODO questions its name.)
- `ControlPane[A]` — a `javafx.scene.layout.Pane` that is itself a `ControlContainer`, usable directly in FXML as a fixed slot. It always mounts replacements into itself at index 0. `initializeContainer` (called from the lookup) wraps its single FXML child (≤ 1 allowed) via `lookupControl`.

Controls not backed by any widget (e.g. `BraceletHidControl`, or a container built over another container) are valid `Control`s; mounting/unmounting is simply a no-op or `None` for them.

### Threading

Control properties are not thread-safe: each control's `defaultProperty` must be written on one thread, and for anything feeding ScalaFX controls that is the JavaFX Application Thread. A control whose value originates elsewhere (e.g. an HID callback) should take a plain `java.util.concurrent.Executor` for its property writes and be given `fxmonad.sfx.FXThreadExecutor`, keeping JavaFX out of its own code. `BraceletHidControl` / `BraceletDeviceMonitor` are the worked example (see `HID_LIFECYCLE_NOTES.md`).

### `SFXProxy` and change replay

`SFXProxy` (in `sfx/SFXProxy.scala`) lets a binding function *construct a fresh control and tweak its properties* without allocating a real widget or losing the caller's existing widget. Every property setter on a proxy (`TextFieldProxy`, `CheckBoxProxy`, `RadioButtonProxy`, `SliderProxy`, `LabelProxy`, `ColorPickerProxy`) records a `Change(propertyName, oldVal, newVal)`; `applyChanges(liveWidget)` replays them (via `Platform.runLater`). The base handles `prefHeight` and `style`; `styleClass` changes are currently swallowed (not recorded) because the `Change` model couldn't represent them correctly. Proxies `throwError` on non-property methods like `buildEventDispatchChain` and `autosize`. When adding a proxy, override `applyChangesPF` and `orElse` the parent's PF, and add a matching `updateFrom` override in the control class. `ToggleGroupProxy` implements the looser `Proxy` trait directly and only replays `selectedToggle` (matched by `userData`).

### Non-data events: `Emitter` / `EventProcessor`

For actions that carry no value (button clicks), an Elm-like message path sits beside `Control`:
- `Emitter[M]` (`Emitter.scala`) wraps a widget and, via `onAction(f: ActionEvent => M)(using EventProcessor[M])`, dispatches a message per action. `sfx/ButtonEmitter` is the only implementation.
- `EventProcessor[M]` (`EventProcessor.scala`) has a registration phase (`registerHandler(PartialFunction[M, Unit])`, first match wins) and a running phase entered by `seal()`; `dispatch` before sealing, or registering after, throws. `SimpleEventProcessor` runs handlers on the FX thread via `Platform.runLater` and logs unmatched messages to stderr (`deadLetter`). That handlers run on the FX thread is a contract any future backend must keep. A `Releasable` given lets `Using.resource(eventProcessor) { ep => ... }` seal at block end.
- `ReceivesEvents[M]` is mixed into a controller to provide `given eventProcessor`; override it to swap backends.

### The macro annotations (`macros/`)

`@FXMonad("someFxId")` on a `lazy val name: Control[T] = ???` in an `@experimental` class (`FXMonad.scala`):
1. Injects a `private @FXML var someFxId: javafx.scene.Node = null` so FXMLLoader wires the node (any `Node`, so `ControlPane`s work too).
2. Rewrites the body to a null check plus `Control.lookupControl[T](someFxId)`, summoning `ClassTag[T]` at the call site; evaluated lazily on first access, after FXML has run.

`@FXEmitter("someFxId")` on a `lazy val name: Emitter[M] = ???` (`FXEmitter.scala`) does the same, but the injected var is typed `javafx.scene.control.Control` and the body calls `FXEmitter.wrap[M]`, which dispatches on widget class (only `Button` today).

For both, the fx:id in the FXML must match the annotation string, and the val name must be *different* from that fx:id (the macro checks this). Neither annotation can be used for a `ToggleGroup` (not a `Node`/`Control`); `BraceletController` declares that `@FXML` var and builds the `ToggleGroupControl` by hand.

### Package layout

- `core/` — library: `Control`, `ControlContainer` (+ `SFXControlContainer`, `ControlPane`), `Emitter`, `EventProcessor`, `ReceivesEvents` in `fxmonad`; ScalaFX controls, proxies, binders, and `ButtonEmitter` in `fxmonad.sfx`; tests.
- `macros/` — `@FXMonad` and `@FXEmitter` (depends on `core` and `scala3-compiler`).
- `testApp/` — demo apps, a live use-case sandbox rather than library code; changes here exercise but don't define the API.
  - `jackflashtech.test` — `MainApp` / `Controller` / `main-screen.fxml` / `styles.css`.
  - `jackflashtech.test.bracelet` — `BraceletApp`, `BraceletController`, `SliderPanelController` (via `fx:include` of `slider-panel.fxml`), `Intensity` value type, and `BraceletHidControl` + `BraceletDeviceMonitor` (hid4java; see `HID_LIFECYCLE_NOTES.md`). Tests in `testApp/src/test/`.

## Roadmap

Planned features, open design questions, and the list of controls still needing implementations are tracked in `ROADMAP.md`. Check it before designing a new feature or redesigning an existing one, so work lines up with the intended direction.
