# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Built with sbt (Scala 3, JVM). All commands run from the repo root.

- `sbt compile` — compile the library and demo.
- `sbt run` — launch the demo `MainApp` (`src/main/scala/jackflashtech/test/`), which loads `main-screen.fxml` and exercises the fxmonad DSL end-to-end. This is the primary way to hand-verify behavior; there is no headless UI harness.
- `sbt test` — run the munit test suite.
- `sbt "testOnly fxmonad.TextFieldControlProxySpec"` — run one test class.
- `sbt scalafmtAll` — format sources (scalafmt 3.10.7, dialect `scala3`; see `.scalafmt.conf`).

Notes:
- The compiler flag `-Yretain-trees` is required (set in `build.sbt`) so the `@FXMonad` macro annotation can inspect val definitions.
- Anything using the `@FXMonad` annotation or `MainApp` must be marked `@experimental` — `MacroAnnotation` is still an experimental Scala 3 feature.
- Tests that touch ScalaFX must run on the JavaFX Application Thread. See `TextFieldControlProxySpec` for the `Platform.startup` / `Platform.runLater` / `CountDownLatch` pattern to follow.

## Architecture

The library builds a monad-like DSL over JavaFX/ScalaFX controls so that a control is typed purely by the *value* it produces or consumes, independent of the underlying widget class.

### The core abstraction: `Control[COut]`

`fxmonad.Control[COut]` (in `src/main/scala/fxmonad/Control.scala`) exposes:
- `defaultProperty: Property[COut, ?]` — the observable value of the control.
- `apply()` / `update(newVal)` — read/write the value.
- `update(c1, ..., cN, f)` — bind this control to one, two, or three input controls. `f` returns a new `Control[COut]`; a `ControlBinder{1,2,3}` subscribes to the inputs and calls `updateValue()` on change.
- `flatMap` — reads the current value and feeds it to `f`.

The DSL syntax `c3(c1, c2) = { (a, b) => aControl }` is Scala's `apply`/`update` sugar over these overloads. The function returns *a Control* (not a raw value); the binder then decides how to reconcile that new control with the existing one — see the reconciliation logic below.

### Type-agnostic controls via `Conversion`

Every concrete control has an outer value type `COut` (what the user sees) and an inner type `CIn` (what the underlying widget carries — e.g. `TextField` uses `String`, `CheckBox` uses `Boolean`, `Slider` uses `Double`). `ControlBase[COut, CIn]` bridges these via two `Conversion` givens supplied at construction; `Control.given` in `Control.scala` provides the standard set (`String↔Int`, `Boolean↔Int`, etc.). To add a new (COut, CIn) pair, add a `given Conversion[...]` there. Errors from conversion are surfaced through `showError` / `clearError`, which concrete controls implement by attaching a tooltip and toggling an `"error"` CSS class.

### The two-layer control model: `ControlContainer` + `SFXControl`

Bindings can *replace* the underlying widget at runtime (e.g. `Controller.initialize` returns a `LabelControlString` when the temperature > 15 and a `TextFieldControlString` otherwise). `ControlContainer[COut]` is the stable outer handle that user code retains; it wraps a `wrappedControl: Control[COut]` that can be swapped. `ControlContainer.replaceControl` mutates the JavaFX scene graph in place (finds the wrapped control's parent `Pane`, removes it, inserts the new node at the same index — must run on the FX thread via `Platform.runLater`). If types don't match or the parent isn't a `Pane`, it falls back to just copying the value.

`SFXControl[COut, CIn, InnerControl]` is the ScalaFX-backed base; the per-widget files under `src/main/scala/fxmonad/sfx/` (`TextFieldControl`, `CheckBoxControl`, `SliderControl`, `LabelControl`, `ColorPickerControl`) extend it and define listeners in both directions (widget → property via `updateProperty`, property → widget via `defaultProperty.onChange`).

### `SFXProxy` and change replay

`SFXProxy` (in `SFXProxy.scala`) is the mechanism that lets a binding function *construct a fresh control and tweak its properties* without allocating a real widget or losing the caller's existing widget instance. Every property setter on a proxy (`TextFieldProxy`, `CheckBoxProxy`, `SliderProxy`, `LabelProxy`, `ColorPickerProxy`) records a `Change(propertyName, oldVal, newVal)` into a list. `ControlBinder.updateValue` detects when the newly-returned control's inner widget is a proxy of the same class as the existing one and calls `proxy.applyChanges(existingWidget)` to replay changes onto it — preserving whatever settings the FXML already established. The proxies also intentionally `throwError` on non-property methods like `buildEventDispatchChain` and `autosize` to catch accidental use of a proxy as if it were a live control. When adding a new proxy class, override `applyChangesPF` and `orElse` the parent's PF so common properties (`style`, `styleClass`, `prefHeight`) stay handled.

### The `@FXMonad` macro annotation

`@FXMonad("someFxId")` on a `lazy val name: Control[T] = ???` in an `@experimental` class (see `Controller.scala`) does two things at expansion time (`FXMonad.scala`):
1. Injects a `private @FXML var someFxId: javafx.scene.control.Control = null` into the enclosing class so JavaFX's FXMLLoader wires the widget from the FXML.
2. Rewrites the `???` body to `FXMonad.lookupControl(classOf[T], someFxId)`, which is evaluated lazily on first access — after FXML has run.

`FXMonad.lookups` is a mutable `AtomicReference[Map[Class[?], List[PartialFunction[javafx.scene.control.Control, Control[?]]]]]`. To register a new (output type, widget) combination, `getAndUpdate` this map before the stage starts (see `MainApp.start` adding `Color` support for `ColorPicker`). The lookup returns the first partial function that matches the actual widget instance, wrapped in a `ControlContainer`.

Consequence for the FXML/controller layer: the fx:id on the FXML `<Control>` element must match the string passed to `@FXMonad`, and the `Control[T]` val name must be *different* from that fx:id (there's an explicit check in the macro).

### Package layout

- `fxmonad` — core (`Control`, `ControlContainer`, `SFXControl`, `SFXProxy` + typed proxies, `@FXMonad` macro).
- `fxmonad.sfx` — concrete ScalaFX-backed controls and `ControlBinder{1,2,3}`.
- `jackflashtech.test` — demo app (`MainApp`, `Controller`) plus `src/main/resources/jackflashtech/test/{main-screen.fxml, styles.css}`. Treat this as a live use-case sandbox, not library code — changes here exercise but don't define the API.
