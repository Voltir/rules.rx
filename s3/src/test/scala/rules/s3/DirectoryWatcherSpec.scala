package rules.s3

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.typed.testkit.TestKitSettings
import akka.typed.scaladsl.adapter._
import org.scalatest._
import com.amazonaws.services.s3.AmazonS3
import org.scalamock.scalatest.MockFactory
import akka.typed.testkit.scaladsl._
import com.amazonaws.services.s3.model.{ObjectListing, S3ObjectSummary}

import scala.concurrent.duration._

trait TypedHelper { self: TestKit =>
  implicit def typedSys: akka.typed.ActorSystem[Nothing] =
    akka.typed.ActorSystem.wrap(system)

  implicit def testkit: TestKitSettings = TestKitSettings(typedSys)
}

class DirectoryWatcherSpec
    extends TestKit(ActorSystem("test-system"))
    with FlatSpecLike
    with MockFactory
    with Matchers
    with TypedHelper
    with AmazonS3Mock
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val somePath = S3Path("", "")
  val cfg = DirectoryWatcher.Config(
    notDetectedInterval = 5.milliseconds,
    unstableInterval = 5.milliseconds
  )

  it should "always eventually signal" in {
    val client = mock[AmazonS3]
    val result = mock[ObjectListing]

    result.using(List.empty)
    client.mockListObjects(result)

    val dirs = new DirectoryWatcher(client, cfg)

    val probe = TestProbe[Boolean]("test-probe")

    val behavior =
      dirs.watch(
        somePath,
        probe.testActor,
        initialDelay = 5.millis
      )(identity)

    val ref = system.spawnAnonymous(behavior)
    probe.expectMsg(false)
    probe.expectMsg(false)
    system.stop(ref.toUntyped)
  }

  it should "wait for bucket to be quiescent" in {
    val client = mock[AmazonS3]
    val result = mock[ObjectListing]

    result.mockSequence(
      List(
        List.empty[S3ObjectSummary],
        List(new S3ObjectSummary()),
        List(new S3ObjectSummary(), new S3ObjectSummary()),
      ))

    client.mockListObjects(result)

    val dirs = new DirectoryWatcher(client, cfg)
    val probe = TestProbe[Boolean]("test-probe")

    val behavior =
      dirs.watch(
        somePath,
        probe.testActor,
        initialDelay = 5.millis
      )(identity)

    val ref = system.spawnAnonymous(behavior)
    probe.expectMsg(false)
    probe.expectMsg(true)
    system.stop(ref.toUntyped)
  }
}
