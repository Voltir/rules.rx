package rules

import rx.Ctx

trait HasOwner {
  implicit def owner: Ctx.Owner
}
