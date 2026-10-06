package fxmonad

import scalafx.application.Platform
import scala.collection.mutable.ListBuffer
import scala.util.Using.Releasable

/** A sink for non-data events (e.g. button clicks) produced by [[Emitter]]s.
  *
  * Handlers are registered as [[PartialFunction]]s over a message type `M` and
  * are consulted in registration order; the first one whose pattern matches a
  * dispatched message handles it. This is the "message bus" edge of an Elm-like
  * architecture: there is no unified model or view re-render, only message
  * dispatch.
  *
  * The processor has two phases:
  *   1. a single-threaded registration phase, during which [[registerHandler]]
  *      is called (typically from a controller's `initialize`), and
  *   2. a running phase, entered by [[seal]], during which [[dispatch]] may be
  *      called (possibly concurrently, for asynchronous backends).
  *
  * Implementations must guarantee that handlers run on the JavaFX Application
  * Thread, so handler bodies may freely read and mutate controls without
  * marshalling. See [[SimpleEventProcessor]].
  *
  * @tparam M
  *   the (typically sealed) message type this processor understands.
  */
trait EventProcessor[M] {

  /** Register a block of handlers. Blocks are consulted in registration order
    * (first match wins), so registration order is match priority.
    *
    * Must be called before [[seal]]; calling it afterwards is an error.
    */
  def registerHandler(handler: PartialFunction[M, Unit]): Unit

  /** End the registration phase and stand up whatever runtime the backend needs
    * (a no-op collapse for the simple backend; actor creation for a Pekko
    * backend). Idempotent.
    */
  def seal(): Unit

  /** Route a message to the first registered handler that matches it. Must be
    * called after [[seal]]. The handler runs on the JavaFX Application Thread.
    */
  def dispatch(message: M): Unit
}

object EventProcessor {

  /** A `scala.util.Using` [[Releasable]] that treats the end of a `Using` block
    * as the end of the registration phase, i.e. it calls
    * [[EventProcessor.seal]] on "release". This is not the usual
    * resource-cleanup sense of `Using`, but it borrows Gradio's block-scoped
    * idiom so registration and sealing read as a single scope:
    * {{{
    *   Using(eventProcessor) { ep =>
    *     ep.registerHandler { case ResetAgeClicked => age() = 0 }
    *     resetAgeEmitter.onAction(_ => ResetAgeClicked)
    *   } // seal() runs here, as the block closes
    * }}}
    */
  given Releasable[EventProcessor[?]] = new Releasable[EventProcessor[?]] {
    override def release(resource: EventProcessor[?]): Unit = resource.seal()
  }
}

/** The default, dependency-free [[EventProcessor]]: it keeps the registered
  * handlers in a list, collapses them into a single [[PartialFunction]] on
  * [[seal]], and dispatches synchronously-scheduled work onto the JavaFX
  * Application Thread via `Platform.runLater`.
  *
  * Because every dispatch is marshalled through `Platform.runLater`, handler
  * bodies always execute on the FX thread — establishing the same threading
  * contract that any future (asynchronous) backend must also honour.
  */
class SimpleEventProcessor[M] extends EventProcessor[M] {

  private val handlers = ListBuffer.empty[PartialFunction[M, Unit]]

  @volatile private var combined: Option[PartialFunction[M, Unit]] = None

  override def registerHandler(handler: PartialFunction[M, Unit]): Unit =
    synchronized {
      if (combined.isDefined) {
        throw new IllegalStateException(
          "Cannot register a handler after the EventProcessor has been sealed."
        )
      }
      handlers += handler
    }: Unit

  override def seal(): Unit =
    synchronized {
      if (combined.isEmpty) {
        combined = Some(
          handlers.toList
            .reduceOption(_ orElse _)
            .getOrElse(PartialFunction.empty)
        )
      }
    }

  override def dispatch(message: M): Unit = {
    val handler = combined.getOrElse(
      throw new IllegalStateException(
        "Cannot dispatch a message before the EventProcessor has been sealed."
      )
    )
    Platform.runLater {
      handler.applyOrElse(message, deadLetter)
    }
  }

  /** Called on the FX thread when no registered handler matches a message.
    * Override to change the policy; the default logs to stderr rather than
    * failing, so an unhandled message never crashes the UI thread.
    */
  protected def deadLetter(message: M): Unit =
    System.err.println(s"No handler matched message: ${message.toString()}")
}
