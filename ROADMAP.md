# Roadmap

This project is still pretty immature, so rather than setting release candidate goals, I've broken it up into features that the project needs before it's minimally usable and the to-do list of controls that need implementations.

## Features

[ ] Ensure @FXMonad macro isn't required

Maybe this project has a bifurcated focus, but I like the macro functionality because I find using JavaFX with ScalaFX cumbersome and ScalaFXML hasn't adapted to Scala 3 macros, but at the same time it's not required for the monadic operating paradigm and requires the client project to have things like the @experimental tag that are probably prohibitive for users. Making sure users use ControlContainer rather than just TextFieldControl (or similar) is an extra bit of documentation and source of possible bugs that hopefully can be mitigated. A controller could be implemented with

```
class Controller {
  var javaControl: javafx.scene.control.TextField = uninitialized
  lazy val scalafxControl: scalafx.scene.control.TextField = TextField(javaControl) // Optional, if you just reference to JavaFX control in the next line
  lazy val monadicControl: fxmonad.Control = ControlContainer(TextFieldControl(scalafxControl))
}
```

[ ] Maybe there should be implementations that just wrap JavaFX

The amount of layering of wrappers here is a pain. It's required to wrap JavaFX controls but it's not necessarily required to wrap ScalaFX controls, which wrap JavaFX controls. They could be distinguished by "import fxmonad.sfx.\_" vs "import fxmonad.jfx.\_"

[ ] Gradio-like easy control initialization

Gradio has a very easy initialization for UIs that don't require much control over layout and styling that looks like 

```
with gr.Blocks() as demo:
    name = gr.Textbox(label="Name")
    output = gr.Textbox(label="Output Box")
    greet_btn = gr.Button("Greet")
    greet_btn.click(fn=greet, inputs=name, outputs=output, api_name="greet")


```
(Taken from [https://gradio.app/guides/blocks-and-event-listeners](here))

I'm not sure if something like this is possible with this kind of integration with JavaFX while preserving the integration with FXML (which is my priority right now. I guess if you want Gradio, use Gradio; obviously, I think Gradio's pretty neat), but if it is, it should be done. I'm not sure why Gradio uses the "with" block; it's a funny use of "with" because it seems to require the initialization be complete by the time the "close" on "demo" happens and instead of discarding the resource "demo" at the end of the block like you would with a database connection it uses that as a signal that initialization is done, but without context parameters I don't understand how the value in the "demo" variable is related to inside the "with" block. Getting it to work in Scala will probably require learning that. Maybe it will be enough to use the @FXML-tagged "initialize" function.

[ ] Support for Future objects

Writing the description below of ComboBox suggests that it should be writable as 

```
provinces(countries) = (country: String) => {
  val states: Future[List[String]] = // Fetch list of states from someplace
  states.map(sts: ComboBoxControl(sts))
}
```

so Control#update[A, B](Control[A], f: (A) => Control[B]) should be matched by Control#update[A, B](Control[A], f: (A) => Future[Control[B]]) or maybe Control#update[F[_]: Monad[F], A, B](Control[A], f: (A) => F[Control[B]])

[ ] Better understanding of SFXProxy: 1. whether there's a better way to "replay" changes made in controller functions, 2. which functions need "throwError" added to them to ensure they aren't accidentally used in unsupported ways, 3. which properties need to be subscribed to to replay changes. I'm torn between only adding support for properties once a use-case has been demonstrated and not wanting to leave things unimplemented because I didn't think of something. I suspect this is a conflict between my normal experience as a corporate services developer and the fact that this is more like a product [Joel Spolsky's 5 worlds](https://www.joelonsoftware.com/2002/05/06/five-worlds/) (What he calls Internal and Shrinkwrap, but I think is better looked at as Service and Product).

## Controls

[ ] Button

This is the next major paradigm adjustment, because exposing a button as Control[Int] or Control[Boolean] could imply that it's exposing the "isPressed" property. It may require something like a trait ControlPublisher or ControlEmitter with syntax like

```
val btn: ControlEmitter = ...
val age: Control[Int] = ...
val name: Control[String] = ...
val output: Control[String] = ...

btn.emit(age, name) = (age: Int, name: String) => {
  output() = s"${name} is ${age} years old"
}
```

but this usage would 1. not permit the replacement of output to another control (possibly not a problem). 2. would restrict "btn" to some default event on which to emit (possibly solvable by subclassing ButtonControlClick and ButtonControlMouseOver, but that's terrible, since it would require different instances to encapsulate the same ScalaFX control), 3. does not include information from "btn" in the function (possibly not a problem).

One alternative is to provide a method for each emission, which doesn't abstract away that it's a button (calls like btn.click(...), btn.mouseOver(...)). Or make the wrapper around ObjectProperty objects like Button#onMouseClicked.

The other one is simply declare this kind of event out-of-scope and just use JavaFX/ScalaFX features as-built. The reason I don't like this is one of my usecases is a wearable that is a touch-sensitive piece of jewelry and I'd like it to emit events on double-click. The adapter of the wearable into a Control[Int] (hold increases the value at a configured rate, click-and-hold causes the value to decrease at a configured rate; imagine a D&D DM surreptitiously signalling to a music-generation AI "increase spookiness of the soundtrack to 7, decrease it to 3; etc.") might then want to be able to emit a "double-click" event (imagine: "Now mix in a roll of thunder"). Adding a non-JavaFX event emission system to the controller undoes some of the purpose of this. However, it's not necessarily a problem to have something like

```
val ring: Control[Int] = ... // Initialized to the "hold" and "click-and-hold" gestures
val rollThunder: ControlEmitter = ... // Initialized to listen to the same wearable, but the "double-click" gesture
val output: Control[String] = ...

output(ring) = (spookiness: Int) => {
  MusicControl("Construct here a prompt that says 'Set the spookiness to ${spookiness}'")
}
rollThunder() = () => {
  output() = "Construct here a prompt that says 'Mix-in a roll of thunder'"
}
```

[ ] ComboBox

This should generally include a feature to bind the list of selections, something like 

```
val combobox: ControlList[String] = ...
val something: List[String] = ...

combobox.list() = {
  something
}
```

that would both make the value represent a String, but also have a feature/property to bind a list of String objects for some reason, like (in this case) selecting from a list of String objects. This would keep the selected object separate from the list of possible selections, because both need to be settable, like

```
@FXMonad("countryList")
val countries: ControlList[String] = ???

@FXMonad("stateList")
val provinces: ControlList[String] = ???

provinces(countries) = (country: String) => {
  val states: Future[List[String]] = // Fetch list of states from someplace
  CombBoxControl(Await.result(states, 3.seconds))
}
```

and this would want to work for ComboBox, ComobList, but also Table without having a "selected" property (This last bit is the difficult one, because that would be a ControlList[DataObject], it wouldn't also be a Control[DataObject]). I think these traits should mix together without requiring each other.

[ ] More controls

This list should actually be expanded to include each control specifically so they can be checked off. I'm just not entirely sure what the list should include. It's probably not worth including some things, like GridPane. If you need control of GridPane, you aren't interested entirely in the monadic form. Should that encourage you away from this library? Would it be prohibitive to mix usage together, make your business logic easier but still enabling you to access the UI controls? It would be nice if there's value to this even when you don't give up accessing JavaFX directly; I'm not sure it's entirely about hiding JavaFX from the developer, only making dealing with it optional and being able to invisibly mix-in other controls systems (like wearables) possible.

[ ] Moving off ROADMAP.md to a better maintained Issues list in GitHub.com

## Project Overhead

[ ] Add code coverage reporting

[ ] Add/configure linter. I added scalafmt and scalafix and ran "scalafix/RemoveUnused", but there's probably more to be done.