# Monadic JavaFX

I work in Scala a lot, I really liked Adobe Flex and JavaFX is almost that good, and I recently learned [Gradio](https://gradio.app), which I thought was pretty cool. So here's my attempt to create a monadic system of controls that are solely typed by the datatype you are expecting from it.

Any control expected to provide or consume an Int, for instance, is a Control[Int]. This is true whether it's a Slider, a TextField that has format checks that enforces that an integer must be typed in, a ComboBox with a collection of integers for selection, etc. This includes, in the future, a non-JavaFX wearable configured such that it's hold gesture increments the provided integer once a second, etc. Any control expected to provide or consume a String is a Control[String]. This could include a CheckBox that you want to provide the strings "true" and "false", or a text-to-speech control that will read the string aloud rather than display in in JavaFX.

Consider:

```
val c1: Control[Int] = ...
val c2: Control[Boolean] = ...
val c3: Control[String] = ...

c3(c1, c2) = { (v1: Int, v2: Boolean) =>
    val newC = TextFieldControl(s"Value is ${v1}")
    if (v2) {
        newC.control.styleClass.add("emphasis")
    }
    newC
}
```

Because c1 is a Control[Int] and c2 is a Control[Boolean], it is known that the function that produces the value for c3 will be f(Int, Boolean): Control[String]. If c3 is based on a TextField, the style updates performed in the function will be displayed. If c3 is some other kind of control, it will be _replaced_ with a TextField, styled as specified. But, whatever happens, c3 is a control that expects to show a String.

A lot of this is done already with property binding. However, this handles type validation, format checking, and frees up the work of extracting the value from controls from different properties. The logic functions are similar in concept to a Monad's "bind" operator (usually flatMap in Scala) and makes different controls more interoperable.

Not much is built yet, but I thought it was a good start and I'm interested in getting feedback on it, or other contributors. Let me know if you think it's going someplace you're interested in.

## Installing

This isn't published anywhere yet, so you would have to build this project and run "sbt publishLocal", then include the following in your project:

```
libraryDependencies += "jackflashtech" %% "fxmonad" % "0.1.0-SNAPSHOT"
```

## Usage

### Controls

Control[A] objects can be bound to a function that converts values of simple data types to the controls that provide that data, including the ability to replace the control being utilized for that data in future. For example, if at one stage of the application, the main business logic is displaying a text that says "The data is not yet fully initialized", but later it replaces that control with a video player, then initially the Control[String] can be implemented with a TextField, but later can be impemented with a video player (with the "String" representing the URL of the video stream) such as:

```
FXML:
<HBox>
    <Label fx:id="theLabel">This data is not yet implemented.</Label>
    <TextField fx:id="textInput" />
</HBox>

Scala:
@FXMonad("textInput")
lazy val url: Control[String] = ???
@FXMonad("theLabel")
lazy val textOutput: Control[String] = ???

@FXML
def initialize = {
    textOutput(url) = { (url: String) =>
        if (url.nonEmpty) { // And check the URL is valid, and is actually a video stream, and ...
            VideoPlayer(url)
        } else {
            LabelControl("The data is not yet fully initialized.")
        }
    }
}
```
(Note: There is no implementation of VideoPlayer like there is in Gradio. This isn't an out-of-the-box JavaFX control, and I'm limiting to those for now)

(Note: See the Macros section for the necessity of using a ControlContainer if you want this functionality and are NOT using a macro.)

Controls that don't change type, like using a LabelControl to replace a LabelControl, have the properties set in the business logic transferred to the existing object.

### Emitters

Emitters are objects that emit events. Because Control masks the underlying control, events are exposed through the Emitter trait to a PartialFunction that handles only events relevant to the application. Controls that emit events are given the opportunity to translate events to message objects, and then those objects are processed in a PartialFunction. Space has been reserved to allow processing of these events in Pekko, Akka, RXScala, etc., but these have not been implemented. (See example below in [Macros](#Macros))

### Macros

Note: To use the macros requires the "@experimental" annotation on the Controller and any class that uses it, because, as of this writing, this style of macro in Scala 3 requires it.

I have include a couple of macros in the project to make integration with ScalaFX easier: FXMonad and FXEmitter. These aren't required to use the project, but they make some things easier when integrating with FXML.

For a control with the fx:id "colorControl", you can put the following in your controller class:

```
FXML:
<StackPane ... fx:controller="Controller">
    <ColorPicker fx:id="colorControl" />
</StackPane>

Scala:
class Controller {
    @FXMonad("colorControl")
    lazy val color: Control[Color] = ???
}
```

and the macro will instantiate a Control[Color] object in the variable "color" that is backed by the FXML tag.

For a button or something that emits an event, you add the following:

```
FXML:
<StackPane ... fx:controller="Controller">
     <Button fx:id="buttonid" text="Click Me" />
</StackPane>

Scala:
sealed trait Msg
case object Clicked extends Msg

class Controller extends ReceivesEvents {
    @FXEmitter("buttonid")
  lazy val button: Emitter[Msg] = ???

    @FXML
    def initialize() = {
        button.onAction(_ => Clicked)

        Using.resource(eventProcessor) { ep =>
            ep.registerHandler {
                case Clicked => // Handle click event
            }
        }
    }
}
```

The default behavior of the macros is to wrap all Control[?] objects in a ControlContainer object, which is a container that allows the business logic to replace a control with another control of the same type, such as a Slider used as a Control[Int] with an HID device that is also used as a Control[Int]. Without the container, the system could only transfer the value of the created control to the control already being used. To initialize a container manually without the macro, use (for example):

```
@FXML
var sliderControl = null

lazy val slider: Slider = ControlContainer(
    new IntegerProperty(),
    SliderControl(sfx.Slider(sliderControl)))
```
(Note: usig a ControlCotainer with Emitters is not tested, but it's on the roadmap.)

## Comparable Projects

The following projects are comparable and may be better developed, but some are not being maintained and can't do everything I'm trying to accomplish:

- [scalafx-utils](https://github.com/sarahgerweck/scalafx-utils)
: This project uses the monadic approach that I'm trying to use, but it does so to 1. all properties, 2. not the controls themselves. As you can see from the roadmap, I'm realizing that this probably needs to implement Control[A] for all properties, not just the entire control, so there's a good chance that I'm going down the wrong road here, but, secondly, I think the approach I'm using of separating the "naive" type of the control from the type the control is supposed to be delivering to the application is useful and being able to dynamically exchange instances that are the same Control[A] type but may not even be JavaFX/ScalaFX controls on the screen is a useful abstraction.

- [cljfx](https://github.com/cljfx/cljfx)
: I haven't used this much, but I've looked at it a little. I based a little of the Emitter class on the way the Elm/TEA pattern uses events, but I'm not sure I wanted to go down the route of mapping from mapping a functional, disposable, declarative object representation of the desired UI and the JavaFX paradigm of controls that have state, etc.

- [Jetpack Compose](https://developer.android.com/compose)
: I like this and I've looked into learning more about it, but 1. I don't like the paradigm of putting display logic in procedural code (part of the goal here is to "Gradio, but integrates FXML", afterall), 2, I'm finding the learning curve of switching to JetPack heavy-going. I have other projects in which I am implemeting the UI in Compose, but those are separate and I wanted to do this.

## Example Projects

There are a few (currently 2) executables exercising the library in the subproject "testApp": jackflashtech.test.MainApp and jackflashtech.test.bracelet.BraceletApp. The first exercises the implemented controls and provides sample code for their use. The second tests the use case of dynamically substituting a Control[...] where one is a Bluetooth Human Interface Device (HID) and the other is a JavaFX Slider.

## Known Limitations

- [ ] I haven't implemented all JavaFX controls yet. I've been expanding the list of implemented controls and it's been exposing more features that are required, but I haven't worked through the entire JavaFX catalog.

- [ ] There's an additional macro required to initialize a custom controller class, which is slightly different from a subclass of javafx.scene.control.Control.

- [ ] Currently, the ability to report errors in a custom fashion is prepared to be injectable/customizable, but it isn't yet.

- [ ] It seems likely that controls should be both a Control and an Emitter. It feels like this blurs the main function of the library, so I'm not sure about it, but it's functionality that is clearly necessary to respond paradigmatically to things like onChange events, which may or may not be needed (see next paragraph).

- [ ] It seems likely that all properties need to be exposable as Control[?] objects. This is necessary for something like enabling a button when a text box is non-empty:

```
val textBox: Control[String] = ...
val buttonDisable: Control[Boolean] = button.disable
...
buttonDisable.m(textBox) = { (text: String) => !text.nonEmpty }
```
(Note: This uses the ".m(...)" syntax, which isn't checked in yet, to avoid instantiating a new control)

- [ ] On the theme of the last couple of notes here, it's probably going to be required to perform binding to Emitter objects to be able to replace and/or use proxies to change the properties of Emitters. This use has been exposed, but not implemented.

- [ ] Implementations for processing Emitter messages on other reactive frameworks like Akka, Pekko, or RXScala have not been implemented.

See the issues list and the ROADMAP.md file for additional known bugs.

## Building and publishing

`sbt package` builds `fxmonad_3-0.1.0-SNAPSHOT.jar` (under `target/scala-3.8.4/`), a single jar containing the `core` and `macros` projects. The `testApp` demo is not included.

`sbt publishLocal` publishes that artifact (`jackflashtech:fxmonad_3:0.1.0-SNAPSHOT`) to the local Ivy repository (`~/.ivy2/local`). Depend on it with:

```scala
libraryDependencies += "jackflashtech" %% "fxmonad" % "0.1.0-SNAPSHOT"
```

Projects using `@FXMonad`/`@FXEmitter` also need `scala3-compiler` on their compile classpath (it is a `Provided` dependency of the artifact).
