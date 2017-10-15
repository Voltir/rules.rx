package rules

trait Register[A <: Rule[_]] {
  def trigger(inp: A#QQ)
}