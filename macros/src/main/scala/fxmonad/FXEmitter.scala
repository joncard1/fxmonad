package fxmonad

import scala.annotation.MacroAnnotation
import scala.annotation.experimental
import scala.quoted.*
import fxmonad.sfx.ButtonEmitter

object FXEmitter {

  /** Wrap an FXML-injected control in the [[Emitter]] appropriate for its
    * widget type. This is the emitter analogue of `FXMonad.lookupControl`;
    * unlike controls, emitters are not keyed by a value type, so the dispatch
    * is purely on the widget class.
    */
  def wrap[M](control: javafx.scene.control.Control): Emitter[M] =
    control match {
      case b: javafx.scene.control.Button =>
        new ButtonEmitter[M](b)
      case other =>
        throw new Exception(
          s"No emitter is defined for a control of type ${other.getClass.getName}"
        )
    }
}

@experimental
class FXEmitter(id: String) extends MacroAnnotation {
  override def transform(using
      quotes: Quotes
  )(
      definition: quotes.reflect.Definition,
      companion: Option[quotes.reflect.Definition]
  ): List[quotes.reflect.Definition] = {
    import quotes.reflect.*

    extension (symb: Symbol)
      def addAnnotation(annotation: Term): Symbol =
        given dotty.tools.dotc.core.Contexts.Context =
          quotes.asInstanceOf[scala.quoted.runtime.impl.QuotesImpl].ctx
        symb
          .asInstanceOf[dotty.tools.dotc.core.Symbols.Symbol]
          .denot
          .addAnnotation(
            dotty.tools.dotc.core.Annotations.ConcreteAnnotation(
              annotation.asInstanceOf[dotty.tools.dotc.ast.tpd.Tree]
            )
          )
        symb

    definition match {
      case ValDef(name, tt, _) =>
        if (name.equals(id))
          report.errorAndAbort(
            s"The name of the emitter, ${name}, should not be the same as the fx:id provided to the annotation."
          )

        val annotationSymbol =
          Symbol.requiredPackage("javafx.fxml").typeMember("FXML")
        val typeTree = TypeTree.of(using annotationSymbol.typeRef.asType)
        val annotationConstructor = annotationSymbol.primaryConstructor
        val jfxControlTypeSymbol =
          Symbol.classSymbol("javafx.scene.control.Control")
        val jfxControlSymbol = Symbol
          .newVal(
            Symbol.spliceOwner,
            id,
            jfxControlTypeSymbol.typeRef,
            Flags.Private | Flags.Mutable,
            Symbol.noSymbol
          )
          .addAnnotation(
            Apply(Select(New(typeTree), annotationConstructor), List())
          )
        val jfxControlRef = Ref(jfxControlSymbol)
        val emitterSymbol = definition.symbol
        val emitterDef = tt.tpe.typeArgs match {
          case Nil =>
            report.errorAndAbort(
              "The Emitter type should have a message type argument"
            )
          case messageType :: _ =>
            val messageTypeTree = TypeTree.of(using messageType.asType)
            val fxEmitterModule = Symbol.requiredModule("fxmonad.FXEmitter")
            val wrapSymbol = fxEmitterModule.methodMember("wrap") match {
              case Nil =>
                report.errorAndAbort("Could not find fxmonad.FXEmitter.wrap")
              case wrap :: _ => wrap
            }
            val nullCheck = '{
              if (
                ${
                  jfxControlRef.asExprOf[javafx.scene.control.Control]
                } == null
              )
                throw Exception(
                  "JavaFX seems not to have initialized the underlying control " + ${
                    Expr(id)
                  }
                )
            }.asTerm
            val wrapCall = Apply(
              TypeApply(
                Select(Ref(fxEmitterModule), wrapSymbol),
                List(messageTypeTree)
              ),
              List(jfxControlRef)
            )
            ValDef(emitterSymbol, Some(Block(List(nullCheck), wrapCall)))
        }

        List(
          ValDef(jfxControlSymbol, Some(Literal(NullConstant()))),
          emitterDef
        )
      case _: Definition =>
        report.errorAndAbort(
          "@FXEmitter can only annotate a val of type Emitter[M]"
        )
    }
  }
}
