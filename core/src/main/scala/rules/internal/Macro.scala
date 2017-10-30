package rules.internal

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.reflect.macros.whitebox

class Impl(val c: whitebox.Context) {
  import c.universe._

  def testIt(annottees: Expr[Any]*): Expr[Any] = {
    val inputs = annottees.map(_.tree).toList
    println(show(inputs))
    val neat = c.prefix.tree match {
      case q"new TestIt(..$x)" =>
        x.collect {
          case q"${Literal(Constant(k: String))} -> $v" =>
            println("AMAZING")
            println(showRaw(k))
            println(showRaw(v))
            val name = TermName(s"an$k")
            q"val $name: ${TypeName(s"$k")} = $v"
          case _ =>
            c.abort(c.enclosingPosition,
                    "Invalid Argument, key must be a constant value!!")
        }.toList
    }

    val output =
      q"""
         trait Wurt {
           def omg: String = "omg"
           ..$neat
         }

         object Wurt {
           def apply(): Wurt = new Wurt { }
           val aaa = 42
           val bbb = false
         }
       """
    println(show(output))
    c.Expr[Any](q"{ $output }")
  }


  val VAR_TYPE = c.mirror.staticClass("rx.Var").toType

  println(VAR_TYPE)
  println(VAR_TYPE)
  println(VAR_TYPE)
  println(VAR_TYPE)
  println(VAR_TYPE.finalResultType)
  println(VAR_TYPE.takesTypeArgs)
  def genInvariantImpl(annottees: Expr[Any]*): Expr[Any] = {

    val inputs = annottees.map(_.tree).toList
    println(show(inputs))

    val target = inputs match {
      case (param: ClassDef) :: Nil =>
        println("Goood")
        param
      case _ => c.abort(c.enclosingPosition, "Only trait may be tagged with the @Invariant annotation")
    }
    println(target.impl)
    println(target.impl.isType)
    println(target.impl.symbol)
    val wurtz = target.impl.children.collect {
      //case thing @ q"val $x: $y" if y.tpe =:= VAR_TYPE => (thing, x, y)
      case thing @ q"val $x: $y" => (thing, x, c.typecheck(y, c.TYPEmode))
    }

    println(wurtz)
    println(wurtz.head._3.isType)
    println(wurtz.head._3.isType)
    println(wurtz.head._3.isType)
    println("!!!!!!!!!!!!!!!!!!!!")
    val wat = wurtz.head._3
    println(wat.tpe)
    println(wat.tpe <:< VAR_TYPE)
    println(wat.tpe.typeSymbol)
    println(VAR_TYPE.typeSymbol)
    println(VAR_TYPE.typeSymbol == VAR_TYPE.typeSymbol)
    c.abort(c.enclosingPosition, "TOOOOOOOOODOOOOO")
    c.Expr[Any](q"{ ??? }")
  }

  def poly[T: c.WeakTypeTag] = {
    c.literal(c.weakTypeOf[T].toString)
  }
}

object Impl {}

@compileTimeOnly("enable macro paradise to expand macro annotations")
class TestIt(inp: (String, Any)*) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro Impl.testIt
}

@compileTimeOnly("")
class Invariant extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro Impl.genInvariantImpl
}

object Argh {
  import akka.typed._

  def argh[T, X]: Behavior[X] = ???
}