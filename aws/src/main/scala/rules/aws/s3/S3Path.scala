package rules.aws.s3

case class S3Path(bucket: String, obj: String) {
  def path: String = s"$bucket/$obj"
}