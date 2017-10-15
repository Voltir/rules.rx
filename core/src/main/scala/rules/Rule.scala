package rules

import rx.Rx

trait Rule[InvarientType] {
  type QQ = InvarientType
  implicit def rxOwnerContext: rx.Ctx.Owner

  def invariant: Rx[InvarientType]
}
