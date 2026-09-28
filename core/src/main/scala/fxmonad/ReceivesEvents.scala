package fxmonad

/** Mixed into a controller class (e.g. `jackflashtech.test.Controller`) to give
  * it an [[EventProcessor]] as a contextual (`given`) value. The controls it
  * owns *emit* events; the controller *receives* them — hence the name.
  *
  * The processor is exposed as a named `given`, so:
  *   - the controller and any sub-components can call
  *     `eventProcessor.registerHandler(...)` during setup and
  *     `eventProcessor.seal()` when registration is complete, and
  *   - [[Emitter#onAction]] can summon it implicitly when wiring a control.
  *
  * To use a different backend (e.g. Pekko or ReactiveX), override the given:
  * {{{
  *   override protected given eventProcessor: EventProcessor[Msg] =
  *     new PekkoEventProcessor[Msg]()
  * }}}
  *
  * @tparam M
  *   the message type shared by this controller's emitters and handlers.
  */
trait ReceivesEvents[M] {
  protected given eventProcessor: EventProcessor[M] = SimpleEventProcessor[M]()
}
