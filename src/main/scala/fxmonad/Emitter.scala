package fxmonad

import javafx.event.ActionEvent

/** The dual of [[Control]]: where a `Control[COut]` is typed by the *value* it
  * carries, an `Emitter[M]` is a *source of discrete events* that carries no
  * value. Wrapping a control such as a button, it turns each user action into a
  * message of type `M` and hands it to the ambient [[EventProcessor]].
  *
  * @tparam M
  *   the message type produced by this emitter.
  */
trait Emitter[M] {

  /** Wire this control's primary action (e.g. a button click) so that each
    * occurrence builds a message via `f` and dispatches it to the given
    * [[EventProcessor]].
    *
    * @param f
    *   builds the message from the triggering `ActionEvent`.
    */
  def onAction(f: ActionEvent => M)(using processor: EventProcessor[M]): Unit
}
