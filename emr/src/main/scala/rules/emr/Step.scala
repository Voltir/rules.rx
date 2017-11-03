package rules.emr

import com.amazonaws.services.elasticmapreduce.model.{
  ActionOnFailure,
  HadoopJarStepConfig,
  StepConfig
}
import rules.s3.S3Path

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
  override def config: StepConfig = {
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
      .withName(stepName.value)
      .withHadoopJarStep(cfg)
      .withActionOnFailure(ActionOnFailure.CONTINUE)
  }
}

case class EmrStreamingStep(
    override val stepName: StepName,
    args: List[String]
) extends Step {
  override def config: StepConfig = {
    val cfg = new HadoopJarStepConfig()
      .withJar("command-runner.jar")
      .withArgs("hadoop-streaming")
      .withArgs(args: _*)

    new StepConfig()
      .withName(stepName.value)
      .withHadoopJarStep(cfg)
      .withActionOnFailure(ActionOnFailure.CONTINUE)
  }
}
