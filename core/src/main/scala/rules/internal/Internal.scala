package rules.internal

import akka.typed.Behavior
import rules.{Register, Signal}

import scala.language.experimental.macros
import scala.reflect.macros._

object Internal {
  def wat[Rules: c.WeakTypeTag](c: blackbox.Context)(
      inp: c.Expr[Int]): c.Expr[Behavior[Nothing]] = {
    import c.universe._

    val rulesTpe = c.weakTypeOf[Rules]
    val registerTpe = c.weakTypeOf[Register[_]]
    val sensorTpe = c.weakTypeOf[Signal[_]]
    val zzzTpe = c.weakTypeOf[rx.Var[_]]

    val registered: List[c.Type] = {
      rulesTpe.decls
        .map(_.typeSignature.finalResultType)
        .filter(x => x <:< registerTpe)
        .toList
    }

    println(
      "------------------------ <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")

    println(registered)

    registered.foreach {
      x =>
        val ruleTpe = x.typeArgs.head
        println(ruleTpe.typeSymbol.fullName)
        println(ruleTpe.typeSymbol.info)
        println(ruleTpe.typeSymbol.info.decls)

        val thingy = ruleTpe.typeSymbol.info
        val neat = ruleTpe.typeSymbol.info.decls.map(_.typeSignature.finalResultType)
          .filter(x => x <:< zzzTpe)
          .toList
        println(neat)


    }
    c.abort(c.enclosingPosition, "Todo")
    c.Expr[Behavior[Nothing]](q"???")
  }
}
