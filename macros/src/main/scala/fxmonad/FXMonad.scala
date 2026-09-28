package fxmonad

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.MacroAnnotation
import scala.annotation.experimental
import scala.quoted.*
import scala.annotation.tailrec
import fxmonad.sfx._
import scalafx.beans.property.{
  BooleanProperty,
  DoubleProperty,
  IntegerProperty,
  StringProperty
}

object FXMonad {
  import fxmonad.Control.given

  val lookups: AtomicReference[Map[Class[?], List[
    PartialFunction[javafx.scene.control.Control, Control[?]]
  ]]] = AtomicReference(
    Map(
      (classOf[String]) -> List({
        case c: javafx.scene.control.TextField =>
          ControlContainer(
            new StringProperty(),
            TextFieldControl[String](scalafx.scene.control.TextField(c))
          )
        case c: javafx.scene.control.CheckBox =>
          ControlContainer(
            new StringProperty(),
            CheckBoxControl[String](scalafx.scene.control.CheckBox(c))
          )
      }),
      (classOf[Int]) -> List({
        case c: javafx.scene.control.CheckBox =>
          ControlContainer(
            new IntegerProperty(),
            CheckBoxControl(scalafx.scene.control.CheckBox(c))
          )
        case c: javafx.scene.control.Slider =>
          ControlContainer(
            new IntegerProperty(),
            SliderControl(scalafx.scene.control.Slider(c))
          )
        case c: javafx.scene.control.TextField =>
          ControlContainer(
            new IntegerProperty(),
            TextFieldControl(scalafx.scene.control.TextField(c))
          )
      }),
      (classOf[Boolean]) -> List({ case c: javafx.scene.control.CheckBox =>
        ControlContainer(
          new BooleanProperty(),
          CheckBoxControl(scalafx.scene.control.CheckBox(c))
        )
      }),
      (classOf[Double]) -> List({ case c: javafx.scene.control.Slider =>
        ControlContainer(
          new DoubleProperty(),
          SliderControl(scalafx.scene.control.Slider(c))
        )
      })
    )
  )

  def lookupControl(
      typ: Class[?],
      control: javafx.scene.control.Control
  ): Control[?] = {
    @tailrec
    def lookupInternal(
        control: javafx.scene.control.Control,
        lookupList: List[
          PartialFunction[javafx.scene.control.Control, Control[?]]
        ]
    ): Option[Control[?]] =
      lookupList match {
        case Nil          => None
        case head :: tail =>
          if (head.isDefinedAt(control)) Some(head(control))
          else lookupInternal(control, tail)
      }

    lookupInternal(control, lookups.get.getOrElse(typ, List())) match {
      case None    => throw new Exception("Failed to find proper control")
      case Some(c) => c
    }
  }
}

@experimental
class FXMonad(id: String) extends MacroAnnotation {
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
            s"The name of the control, ${name}, should not be the same as the fx:id provided to the annotation."
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
        val controlSymbol = definition.symbol
        val controlDef = tt.tpe.typeArgs match {
          case Nil =>
            report.errorAndAbort(
              "The Control type should have 2 type arguments"
            )
          case controlType :: _ =>
            val controlTypeTree = TypeTree.of(using controlType.asType)
            Symbol
              .requiredPackage("scala.Predef")
              .methodMember("classOf") match {
              case Nil =>
                report.errorAndAbort(
                  "Something has gone very wrong if I can't find scala.Predef.classOf"
                )
              case classOfSymbol :: _ =>
                val classOfTerm =
                  TypeApply(Ref(classOfSymbol), List(controlTypeTree))
                ValDef(
                  controlSymbol,
                  Some('{
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
                    FXMonad.lookupControl(
                      ${ classOfTerm.asExprOf[Class[?]] },
                      ${
                        jfxControlRef.asExprOf[javafx.scene.control.Control]
                      }
                    )
                  }.asTerm)
                )
            }
        }
        List(
          ValDef(jfxControlSymbol, Some(Literal(NullConstant()))),
          controlDef
        )
      case _: Definition =>
        report.errorAndAbort("Don't know what to do with this")
    }
  }
}
