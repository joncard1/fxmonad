## Monadic JavaFX

I work in Scala a lot, I really liked Adobe Flex and JavaFX is almost that good, and I recently learned [Gradio](https://gradio.app), which I thought was pretty cool. So here's my attempt to create a monadic system of controls that are solely typed by the datatype you are expecting from it.

Any control expected to provide or consume an Int, for instance, is a Control[Int, ?]. This is true whether it's a Slider, a TextField that has format checks that enforces that an integer must be typed in, a ComboBox with a collection of integers for selection, etc. This includes, in the future, a non-JavaFX wearable configured such that it's hold gesture increments the provided integer once a second, etc. Any control expected to provide or consume a String is a Control[String, ?]. This could include a CheckBox that you want to provide the strings "true" and "false", or a text-to-speech control that will read the string aloud rather than display in in JavaFX.

Consider:

```
val c1: Control[Int, ?] = ...
val c2: Control[Boolean, ?] = ...
val c3: Control[String, ?] = ...

c3(c1, c2) = { (v1: Int, v2: Boolean) =>
    val newC = TextFieldControlString()
    if (v2) {
        newC.control.styleClass.add("emphasis")
    }
    newC.defaultProperty() = s"Value is ${v1}"
    newC
}
```

Because c1 is a Control[Int, ?] and c2 is a Control[Boolean, ?], it is known that the function that produces the value for c3 will be f(Int, Boolean): Control[String, ?]. If c3 is based on a TextField, the style updates performed in the function will be displayed. If c3 is some other kind of control, the plan is it will be _replaced_ with a TextField, styled as specified. But, whatever happens, c3 is a control that expects to show a String.

A lot of this is done already with property binding. However, this handles type validation, format checking, and frees up the work of extracting the value from controls from different properties. The logic functions are similar in concept to a Monad's "bind" operator (usually flatMap in Scala) and makes different controls more interoperable.

Not much is built yet, but I thought it was a good start and I'm interested in getting feedback on it, or other contributors. Let me know if you think it's going someplace you're interested in.