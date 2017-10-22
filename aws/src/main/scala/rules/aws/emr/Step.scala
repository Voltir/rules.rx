package rules.aws.emr

import com.amazonaws.services.elasticmapreduce.model.{
  ActionOnFailure,
  HadoopJarStepConfig,
  StepConfig
}
import rules.aws.s3.S3Path

trait Step {
  def stepName: StepName
  def config: StepConfig
}

case class StepName(value: String) extends AnyVal

case class SparkJarStep(
    mainClass: String,
    jarLocation: S3Path,
    args: List[String],
    override val stepName: StepName
) extends Step {
  override def config = {
    val cfg = new HadoopJarStepConfig()
      .withJar("command-runner.jar")
      .withArgs(
        "spark-submit",
        "--deploy-mode",
        "cluster",
        "--class",
        mainClass,
        jarLocation.path
      )
      .withArgs(args: _*)

    new StepConfig()
      .withName(s"${stepName.value}")
      .withHadoopJarStep(cfg)
      .withActionOnFailure(ActionOnFailure.CONTINUE)
  }
}
