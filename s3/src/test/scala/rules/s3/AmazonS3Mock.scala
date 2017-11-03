package rules.s3

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{
  ListObjectsRequest,
  ObjectListing,
  S3ObjectSummary
}
import org.scalamock.handlers.CallHandler1
import org.scalamock.scalatest.MockFactory

import scala.collection.JavaConverters._

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
    def using(inp: List[S3ObjectSummary]): Unit = {
      (result.isTruncated _).expects().anyNumberOfTimes().onCall { () =>
        false
      }

      (result.getObjectSummaries _).expects().anyNumberOfTimes().onCall { () =>
        inp.asJava
      }
    }

    def mockSequence(seq: List[List[S3ObjectSummary]]): Unit = {
      (result.isTruncated _).expects().anyNumberOfTimes().onCall { () =>
        false
      }

      def loop(remaining: List[List[S3ObjectSummary]]): Unit = remaining match {
        case Nil =>
          (result.getObjectSummaries _)
            .expects()
            .anyNumberOfTimes()
            .onCall(() => List.empty.asJava)

        case h :: Nil =>
          println("HOLD HERE!")
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
