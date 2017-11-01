package rules.s3

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import akka.typed.testkit.TestKitSettings
import akka.typed.scaladsl.adapter._
import org.scalatest._
import com.amazonaws.services.s3.AmazonS3
import org.scalamock.scalatest.MockFactory
import akka.typed.testkit.scaladsl._
import com.amazonaws.services.s3.model.{
  ListObjectsRequest,
  ObjectListing,
  S3ObjectSummary
}
import org.scalamock.handlers.CallHandler1
import org.scalamock.specs2.MockContext

import scala.concurrent.duration._
import scala.collection.JavaConverters._

trait TypedHelper { self: TestKit =>
  implicit def typedSys: akka.typed.ActorSystem[Nothing] =
    akka.typed.ActorSystem.wrap(system)

  implicit def testkit: TestKitSettings = TestKitSettings(typedSys)
}

trait AmazonS3Mock { self: MockFactory =>

  implicit class MockedAmazonS3(client: AmazonS3) {
    def mockListObjects(result: ObjectListing)
      : CallHandler1[ListObjectsRequest, ObjectListing] = {
      (client
        .listObjects(_: com.amazonaws.services.s3.model.ListObjectsRequest))
        .expects(*)
        .anyNumberOfTimes()
        .onCall { (a: ListObjectsRequest) =>
          result
        }
    }
  }

  implicit class MockedObjectListing(result: ObjectListing) {
    def using(inp: List[S3ObjectSummary]) = {
      (result.isTruncated _).expects().anyNumberOfTimes().onCall { () =>
        false
      }

      (result.getObjectSummaries _).expects().anyNumberOfTimes().onCall { () =>
        inp.asJava
      }
    }

    def mockSequence(seq: List[List[S3ObjectSummary]]) = {
      (result.isTruncated _).expects().anyNumberOfTimes().onCall { () =>
        false
      }

      def loop(remaining: List[List[S3ObjectSummary]]): Unit = remaining match {
        case h :: Nil =>
          (result.getObjectSummaries _)
            .expects()
            .anyNumberOfTimes()
            .onCall(() => h.asJava)

        case h :: tail =>
          (result.getObjectSummaries _).expects().once().onCall(() => h.asJava)
          loop(tail)
      }
      loop(seq)
    }
  }
}

class DirectoryWatcherSpec
    extends TestKit(ActorSystem("Stab"))
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
