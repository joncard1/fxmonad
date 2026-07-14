package jackflashtech.fxmonad

import scala.annotation.MacroAnnotation
import scala.quoted.*
import scala.annotation.experimental
import javafx.fxml.FXML
import scalafx.scene.control.TextField
import scala.compiletime.uninitialized
import scala.reflect.ClassTag
import scalafx.scene.control.CheckBox
import jackflashtech.fxmonad._
import jackflashtech.fxmonad.sfx.{CheckBoxControlBoolean, CheckBoxControlInt, CheckBoxControlString}
import jackflashtech.fxmonad.sfx.{TextFieldControlInt, TextFieldControlString}

/**
  * This is a macro annotation that initializes a class correctly to create a controller for JavaFX, and sets the properties up to use the FXMonad system.
  *
  * @param id
  * @param controlTypeName
  */
@experimental
class FXMonad(id: String, controlTypeName: String) extends MacroAnnotation {

    override def transform(using quotes: Quotes)(definition: quotes.reflect.Definition, companion: Option[quotes.reflect.Definition]): List[quotes.reflect.Definition] = {
        import quotes.reflect.*
        import Control.given

        // This is the bit that looks like a bad idea from https://stackoverflow.com/questions/75669835/add-annotation-to-a-method-defined-using-a-symbol-newmethod
        extension (symb: Symbol)
            def addAnnotation(annotation: Term): Symbol =
                given dotty.tools.dotc.core.Contexts.Context =
                quotes.asInstanceOf[scala.quoted.runtime.impl.QuotesImpl].ctx
                symb.asInstanceOf[dotty.tools.dotc.core.Symbols.Symbol]
                    .denot
                    .addAnnotation(
                        dotty.tools.dotc.core.Annotations.ConcreteAnnotation(
                            annotation.asInstanceOf[dotty.tools.dotc.ast.tpd.Tree]
                        )
                    )
                symb


        definition match {
            case ValDef(name, tt, term) =>   
                val annotationSymbol = Symbol.requiredPackage("javafx.fxml").typeMember("FXML")
                val typeTree = TypeTree.of(using annotationSymbol.typeRef.asType)
                val annotationConstructor = annotationSymbol.primaryConstructor
                val jfxControlTypeSymbol = Symbol.classSymbol(controlTypeName)
                val jfxControlSymbol =  Symbol.newVal(Symbol.spliceOwner, id, jfxControlTypeSymbol.typeRef, Flags.Private | Flags.Mutable, Symbol.noSymbol).addAnnotation(Apply(Select(New(typeTree), annotationConstructor), List()))
                if (!jfxControlSymbol.isDefinedInCurrentRun) {
                    report.errorAndAbort(s"Failed to find member $id of ${Symbol.spliceOwner.toString()}")
                }
                val jfxControlRef = Ref(jfxControlSymbol)
                val jfxControlDef = ValDef(jfxControlSymbol, Some(Literal(NullConstant())))

                val controlSymbol = definition.symbol
                
                // I wanted this to perform this match at runtime so that it would be extensible, but I couldn't get it to work. I wanted something like:
                // {
                //  val matchers: List[PartialFunction[javafx.scene.control.Control, Control[A]]] = lookupTableOfSomekind(classOf[tt.tpe.asType]) 
                //  matchers.reduce(...) or something that chains the PartialFunction list together with orElse, something like collect but in reverse, doing
                //  ${jfxControlRef.asExprOf[javafx.scene.control.Control]} match {
                //      case c: TextField => ...
                //      case c: CheckBox => ...
                //      ...
                //  }
                // }
                // And users could provide a collection of PartialFunction[javafx.scene.control.Control, Control[A]] that get chained together.
                val controlDef = (jfxControlTypeSymbol.typeRef.asType, tt.tpe.asType) match {
                    case ('[javafx.scene.control.TextField], '[Control[Int, ?]]) => 
                        ValDef(controlSymbol, Some(
                            '{
                                ControlContainer(TextFieldControlInt(new TextField(${jfxControlRef.asExprOf[javafx.scene.control.TextField]})))
                            }.asTerm
                        ))
                    case ('[javafx.scene.control.TextField], '[Control[String, ?]]) =>
                        ValDef(controlSymbol, Some(
                            '{
                                ControlContainer(TextFieldControlString(new TextField(${jfxControlRef.asExprOf[javafx.scene.control.TextField]})))
                            }.asTerm
                        ))
                    case ('[javafx.scene.control.CheckBox], '[Control[Boolean, ?]]) =>
                        ValDef(controlSymbol, Some(
                            '{
                                ControlContainer(CheckBoxControlBoolean(new CheckBox(${jfxControlRef.asExprOf[javafx.scene.control.CheckBox]})))
                            }.asTerm
                        ))
                    case ('[javafx.scene.control.CheckBox], '[Control[Int, ?]]) =>
                        ValDef(controlSymbol, Some(
                            '{
                                ControlContainer(CheckBoxControlInt(new CheckBox(${jfxControlRef.asExprOf[javafx.scene.control.CheckBox]})))
                            }.asTerm
                        ))
                    case ('[javafx.scene.control.CheckBox], '[Control[String, ?]]) =>
                        ValDef(controlSymbol, Some(
                            '{
                                ControlContainer(CheckBoxControlString(new CheckBox(${jfxControlRef.asExprOf[javafx.scene.control.CheckBox]})))
                            }.asTerm
                        ))
                    case (x, y) => report.errorAndAbort(s"Unrecognized combination of controls and types: ${jfxControlTypeSymbol.typeRef.show} and ${tt.tpe.show}")
                }
                List(jfxControlDef, controlDef)
            case _: Definition => 
                report.errorAndAbort("Don't know what to do with this")
        }
    }
}
