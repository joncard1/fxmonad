package fxmonad.sfx

import scala.util.Using.Releasable
import fxmonad._

object ControlBinder {
  given Releasable[ControlBinder[?]] = new Releasable[ControlBinder[?]] {
    override def release(resource: ControlBinder[?]): Unit = resource.dispose()
  }
}

trait ControlBinder[A](outputControl: Control[A]) {
  protected[fxmonad] def updateValueInner(): Control[A]
  protected[fxmonad] def updateValue(): Unit = {
    val newC = updateValueInner()
    // ControlContainer knows how to reconcile a new Control against the one
    // it's currently wrapping; anything else just tracks the value.
    outputControl match {
      case container: ControlContainer[A] => container.replaceControl(newC)
      case _ => outputControl.defaultProperty() = newC.defaultProperty()
    }
  }
  def dispose(): Unit
}

class ControlBinder1[A, B](
    c1: Control[A],
    outputControl: Control[B],
    f: (A) => Control[B]
) extends ControlBinder[B](outputControl) {

  override protected[fxmonad] def updateValueInner(): Control[B] = {
    c1.flatMap(x1 => {
      f(x1)
    })
  }
  val subscription1 = c1.defaultProperty.onChange({
    updateValue()
    ()
  })

  override def dispose(): Unit = {
    subscription1.cancel()
  }
}

class ControlBinder2[A, B, C](
    c1: Control[A],
    c2: Control[B],
    outputControl: Control[C],
    f: (A, B) => Control[C]
) extends ControlBinder[C](outputControl) {
  protected[fxmonad] def updateValueInner(): Control[C] = {
    c1.flatMap(x1 =>
      c2.flatMap(x2 => {
        f(x1, x2)
      })
    )
  }
  val subscription1 = c1.defaultProperty.onChange({
    updateValue()
    ()
  })
  val subscription2 = c2.defaultProperty.onChange({
    updateValue()
    ()
  })

  override def dispose(): Unit = {
    subscription1.cancel()
    subscription2.cancel()
  }
}

class ControlBinder3[A, B, C, D](
    c1: Control[A],
    c2: Control[B],
    c3: Control[C],
    outputControl: Control[D],
    f: (A, B, C) => Control[D]
) extends ControlBinder[D](outputControl) {
  protected[fxmonad] def updateValueInner(): Control[D] = {
    c1.flatMap(x1 =>
      c2.flatMap(x2 =>
        c3.flatMap(x3 => {
          f(x1, x2, x3)
        })
      )
    )
  }

  val subscription1 = c1.defaultProperty.onChange({
    updateValue()
    ()
  })
  val subscription2 = c2.defaultProperty.onChange({
    updateValue()
    ()
  })
  val subscription3 = c3.defaultProperty.onChange({
    updateValue()
    ()
  })
  override def dispose(): Unit = {
    subscription1.cancel()
    subscription2.cancel()
    subscription3.cancel()
  }
}
