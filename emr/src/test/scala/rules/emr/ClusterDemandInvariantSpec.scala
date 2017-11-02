package rules.emr

import com.amazonaws.services.elasticmapreduce.model.StepConfig
import org.scalatest.{FlatSpec, Matchers}
import rules.HasOwner
import rx.{Ctx, Var}

class ClusterDemandInvariantSpec extends FlatSpec with Matchers {

  class TestInvariant extends ClusterDemandInvariant with HasOwner {
    override implicit lazy val owner: Ctx.Owner = rx.Ctx.Owner.Unsafe.Unsafe
  }

  def fakeStep(name: String): Step = new Step {
    override val stepName: StepName = StepName(name)
    override val config: StepConfig = new StepConfig().withName(name)
  }

  it should "have be empty by default" in {
    val test = new TestInvariant
    test.invariant.now shouldBe List.empty
  }

  it should "have be active if there is demand and step state has been detected" in {
    val test = new TestInvariant
    test.demand() = List(fakeStep("foo"))
    test.detected() = Some(Map.empty)
    test.invariant.now.length shouldBe 1
  }

  it should "not return a value if step state has not been detected" in {
    val test = new TestInvariant
    test.demand() = List(fakeStep("foo"))
    test.invariant.now.length shouldBe 0
  }

  it should "not reschedule a step if it already did so" in {
    val test = new TestInvariant
    test.scheduled() = Set(fakeStep("foo").stepName)
    test.detected() = Some(Map.empty)

    test.invariant.now.length shouldBe 0
    test.demand() = List(fakeStep("foo"))
    test.invariant.now.length shouldBe 0
    test.demand() = List(fakeStep("foo"), fakeStep("bar"))
    test.invariant.now.length shouldBe 1
  }

  it should "clear scheduled if demand signals success" in {
    val test = new TestInvariant
    test.demand() = List(fakeStep("foo"))
    test.detected() = Some(Map.empty)
    test.invariant.now.length shouldBe 1

    //Pretend step was scheduled
    test.scheduled() = Set(fakeStep("foo").stepName)
    test.invariant.now.length shouldBe 0

    //Pretend step completed successfully
    test.demand() = List.empty

    test.invariant.now.length shouldBe 0
    test.scheduled.now.size shouldBe 0
  }

}
