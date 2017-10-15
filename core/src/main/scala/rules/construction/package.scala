package rules

import scala.language.experimental.macros

package object construction {

  def generate[Rules](inp: Int) = macro internal.Internal.wat[Rules]
}
