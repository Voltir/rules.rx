package rules.s3

import akka.typed.{ActorRef, Behavior}
import akka.typed.scaladsl.{Actor, TimerScheduler}
import com.amazonaws.services.s3.model.ListObjectsRequest
import com.amazonaws.services.s3.AmazonS3

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.Try

class DirectoryWatcher(client: AmazonS3, config: DirectoryWatcher.Config) {
  sealed trait Command

  private sealed trait Internal extends Command
  private case object Tick extends Internal
  private case object TickKey

  //Only notify when the directory seems to be done being modified, ie is "quiescent"
  private sealed trait State
  private case object NotDetected extends State
  private case class Unstable(numObjs: Int) extends State
  private case class Stable(numObjs: Int) extends State

  def watch[T](
      path: S3Path,
      notify: ActorRef[T],
      initialDelay: FiniteDuration = 1.second
  )(f: Boolean => T): Behavior[Command] = Actor.withTimers { timers =>
    timers.startSingleTimer(TickKey, Tick, initialDelay)
    impl(path, NotDetected, notify, f, timers)
  }

  //-1 if consecutive calls exceeded max
  private def check(path: S3Path,
                    marker: Option[String] = None,
                    acc: Int = 0,
                    consecutive: Int = 0): Try[Int] = {
    Try {
      val req = new ListObjectsRequest()
        .withBucketName(path.bucket)
        .withPrefix(path.obj)
        .withDelimiter("/")

      val resp = marker match {
        case Some(m) => client.listObjects(req.withMarker(m))
        case None    => client.listObjects(req)
      }

      val cnt = resp.getObjectSummaries.size() + acc
      (resp, cnt)
    }.flatMap {
      case (resp, cnt) =>
        if (resp.isTruncated && consecutive <= config.maxConsecutiveQueries) {
          check(path, Some(resp.getMarker), cnt, consecutive + 1)
        } else if (resp.isTruncated) Try(-1)
        else Try(cnt)
    }
  }

  private def impl[T](
      path: S3Path,
      state: State,
      notify: ActorRef[T],
      f: Boolean => T,
      timers: TimerScheduler[Command]
  ): Behavior[Command] = {

    def next(next: State, period: FiniteDuration): Behavior[Command] = {
      next match {
        case Stable(_) =>
          notify ! f(true)
        case NotDetected =>
          notify ! f(false)
        case _ =>
      }
      timers.startSingleTimer(TickKey, Tick, period)
      impl(path, next, notify, f, timers)
    }

    def stable(cnt: Int): Behavior[Command] =
      next(Stable(cnt), config.stableInterval)

    def unstable(cnt: Int): Behavior[Command] =
      next(Unstable(cnt), config.unstableInterval)

    def detecting: Behavior[Command] =
      next(NotDetected, config.notDetectedInterval)

    Actor.immutable { (ctx, msg) =>
      msg match {
        case Tick =>
          val result = check(path)
          if (result.isFailure) {
            ctx.system.log
              .warning("Failed to get data from S3!: {}", result.failed.get)
          }
          result
            .map { size =>
              state match {
                case NotDetected if size > 0  => unstable(size)
                case NotDetected if size == 0 => detecting

                case Unstable(prev) if size >= 0 =>
                  if (prev == size) stable(size)
                  else unstable(size)

                case Stable(prev) if size == prev => stable(size)
                case Stable(_) if size == -1      => stable(size)
                case Stable(_) if size == 0       => detecting
                case Stable(_)                    => unstable(size)

                case _ => stable(size)
              }
            }
            .getOrElse {
              timers.startSingleTimer(TickKey, Tick, config.notDetectedInterval)
              Actor.same
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
