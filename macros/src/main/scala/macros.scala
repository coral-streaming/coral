package com.natalinobusa

package object macros {

  import scala.language.experimental.macros
  import scala.reflect.macros.blackbox.Context

  // write macros here
  def expose(xs: Any*): PartialFunction[Any, Unit] = macro impl

  def impl(c: Context)(xs: c.Tree*): c.Tree = {
    import c.universe._
    val properties = xs.map { x =>
      x match {
        case q"($a,$b,$c)" => {
          s"""$a : {"type":$b}"""
        }
        case _ => ""
      }
    }

    val q"{ case $wildcardCase }" = q"{case _ => sender ! JNothing}"

    val cases = xs.map { x =>
      x match {
        case q"($a,$b,$c)" =>
          val q"{ case $ast }" = q"{case $a => sender ! render($a -> $c)}"
          ast
      }
    }

    val schemaProps = properties.mkString(",")
    val schema  = s"""{"title":"json schema", "type":"object", "properties":{ $schemaProps } }"""
    val ast = q"""
        {
          case ListFields => {
            val jsonSchema = parse($schema)
            sender ! jsonSchema
          }
          case GetField(field:String) =>
            field match { case ..$cases case $wildcardCase}
        }
    """
    //println(showCode(ast))
    ast
  }

  // write macros here
  def getName(x: Any): String = macro implGetName

  def implGetName(c: Context)(x: c.Tree): c.Tree = {
    import c.universe._
    val p = x match {
      case Ident(s) => s.toString
      case Select(_, TermName(s)) => s
      case _ => sys.error(s"getName: could not extract a name")
    }
    q"$p"
  }

}



