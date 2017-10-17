package rules.aws.s3

import akka.typed.{ActorRef, ActorSystem, Behavior}
import akka.typed.scaladsl.{Actor, TimerScheduler}
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.ListObjectsRequest
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}

import scala.annotation.tailrec
import scala.concurrent.duration._

class DirectoryWatcher(client: AmazonS3, config: DirectoryWatcher.Config) {
  sealed trait Command
  case object Todo extends Command

  sealed trait Internal extends Command
  case object Tick extends Internal

  case object TickKey

  //Only notify when the directory seems to be done being modified, ie is "quiescent"
  sealed trait State
  case object NotDetected extends State
  case class Unstable(numObjs: Int) extends State
  case class Stable(numObjs: Int) extends State

  def watch(
      path: S3Path,
      notify: ActorRef[Option[S3Path]]
  ): Behavior[Command] = Actor.withTimers { timers =>
    timers.startSingleTimer(TickKey, Tick, 1.second)
    impl(path, NotDetected, notify, timers)
  }

  //-1 if consecutive calls exceeded max
  @tailrec
  private def check(path: S3Path,
                    marker: Option[String] = None,
                    acc: Int = 0,
                    consecutive: Int = 0): Int = {
    val req = new ListObjectsRequest()
      .withBucketName(path.bucket)
      .withPrefix(path.obj)
      .withDelimiter("/")

    val resp = marker match {
      case Some(m) => client.listObjects(req.withMarker(m))
      case None    => client.listObjects(req)
    }

    println(client.doesBucketExistV2(path.bucket))
    println(client.doesObjectExist(path.bucket, path.obj))
    println(path.path)
    println(path.path)
    println(resp)
    val cnt = resp.getObjectSummaries.size() + acc

    resp.getMarker
    if (resp.isTruncated && consecutive <= config.maxConsecutiveQueries) {
      check(path, Some(resp.getMarker), cnt, consecutive + 1)
    } else if (resp.isTruncated) -1
    else cnt
  }

  private def impl(
      path: S3Path,
      state: State,
      notify: ActorRef[Option[S3Path]],
      timers: TimerScheduler[Command]
  ): Behavior[Command] = {

    def notifyStable(cnt: Int,
                     timers: TimerScheduler[Command]): Behavior[Command] = {
      println("GO STABLE!")
      notify ! Some(path)
      timers.startSingleTimer(TickKey, Tick, config.stableInterval)
      impl(path, Stable(cnt), notify, timers)
    }

    def unstable(cnt: Int,
                 timers: TimerScheduler[Command]): Behavior[Command] = {
      println("GO UNSTABLE!")
      timers.startSingleTimer(TickKey, Tick, config.unstableInterval)
      impl(path, Unstable(cnt), notify, timers)
    }

    def detecting(timers: TimerScheduler[Command]): Behavior[Command] = {
      println("GO NOT DETECTED!")
      notify ! None
      timers.startSingleTimer(TickKey, Tick, config.notDetectedInterval)
      impl(path, NotDetected, notify, timers)
    }

    println("!?")
    Actor.immutable { (_, msg) =>
      msg match {
        case Tick =>
          println("--- TICK ---")
          val size = check(path)
          println(s"----------> SIZE: $size ($state) <---------------")
          state match {
            case NotDetected if size > 0  => unstable(size, timers)
            case NotDetected if size == 0 => detecting(timers)

            case Unstable(prev) if size >= 0 =>
              if (prev == size) notifyStable(size, timers)
              else unstable(size, timers)

            case Stable(prev) if size == prev => notifyStable(size, timers)
            case Stable(_) if size == -1      => notifyStable(size, timers)
            case Stable(_)                    => unstable(size, timers)

            case _ => notifyStable(size, timers)
          }

      }
    }
  }
}

object DirectoryWatcher {
  case class Config(
      // Polling Interval when in various states
      notDetectedInterval: FiniteDuration = 5.minutes,
      stableInterval: FiniteDuration = 30.minutes,
      unstableInterval: FiniteDuration = 2.minutes,
      // Max number of consecutive queries to listObjects api - if this is hit, bucket is considered stable!
      maxConsecutiveQueries: Int = 5
  )
}

object Test extends App {
  import scala.io.StdIn
  import concurrent.duration._

  val temp: Behavior[Option[S3Path]] = Actor.immutable { (_, msg) =>
    println(s"Noice: $msg")
    Actor.same
  }

  val s3Client = AmazonS3Client
    .builder()
    //.withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
    .withCredentials(new ProfileCredentialsProvider("valkyrie-prod"))
    .withRegion(Regions.US_WEST_2)
    .build()

  val root = Actor.deferred[Nothing] { ctx =>
    val path = S3Path("temp",
                      "test/foo/partition_date=2017-10-17/")
    val tmp = ctx.spawn(temp, "temp-watcher-thing")

    val cfg = DirectoryWatcher.Config(
      notDetectedInterval = 5.seconds,
      stableInterval = 1.minute,
      unstableInterval = 15.seconds,
    )

    val watcher = new DirectoryWatcher(s3Client, cfg)
    val test = ctx.spawn(watcher.watch(path, tmp), "dir-neat")
    Actor.empty
  }

  val system = ActorSystem[Nothing](root, "HelloWorld")
  try {
    println("Press ENTER to exit the system")
    StdIn.readLine()
  } catch {
    case e: Exception =>
      println("---------------------> NOOOOOOOOOOOo <----------------")
      println(e.getMessage)
  } finally {
    println("DEATH!")
    system.terminate()
  }
}
