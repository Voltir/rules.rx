package rules.aws.s3

import rules.behaviors._
import akka.typed.{ActorRef, ActorSystem, Behavior}
import akka.typed.scaladsl.Actor
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}

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

object Test extends App {
  import scala.io.StdIn
  import concurrent.duration._

  val temp: Behavior[Option[S3Path]] = Actor.immutable { (_, msg) =>
    println(s"Noice: $msg")
    Actor.same
  }

  val s3Client = AmazonS3Client
    .builder()
    .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
    .withRegion(Regions.US_WEST_2)
    .build()

  val root = Actor.deferred[Nothing] { ctx =>
    val path = S3Path("test-bucket", "test")
    val tmp = ctx.spawn(temp, "temp-watcher-thing")
    val watcher = new FileWatcher(s3Client, 5.second)
    val test = ctx.spawn(watcher.watch(path, 30.seconds, tmp), "neat")
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
