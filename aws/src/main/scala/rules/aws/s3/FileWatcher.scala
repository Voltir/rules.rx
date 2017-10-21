package rules.aws.s3

import rules.behaviors._
import akka.typed.{ActorRef, Behavior}
import akka.typed.scaladsl.Actor
import com.amazonaws.services.s3.AmazonS3

import scala.concurrent.duration.FiniteDuration

class FileWatcher(client: AmazonS3, pollInterval: FiniteDuration) {
  import concurrent.duration._

  // Behavior Messages
  sealed trait Command
  sealed trait Internal extends Command

  case object Tick extends Internal

  // Timer Key
  case object PollingKey

  def watch(path: S3Path,
            lifetime: FiniteDuration,
            notify: ActorRef[Option[S3Path]]): Behavior[Command] = {
    finiteRepeat[Command](lifetime)(Tick, PollingKey) { timers =>
      Actor.immutable { (ctx, msg) =>
        msg match {
          case Tick =>
            try {
              if (client.doesBucketExistV2(path.bucket) &&
                  client.doesObjectExist(path.bucket, path.obj)) {
                notify ! Some(path)
              } else {
                notify ! None
              }
            } catch {
              case e: Exception =>
                ctx.system.log.warning("Unexpected error is s3 watcher {}", e)
            } finally {
              timers.startSingleTimer(PollingKey, Tick, pollInterval)
            }
        }
        Actor.same
      }
    }
  }
}