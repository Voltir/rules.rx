package rules.aws.emr

import com.amazonaws.services.elasticmapreduce.model.{
  ActionOnFailure,
  HadoopJarStepConfig,
  StepConfig
}
import com.amazonaws.services.elasticmapreduce.util.StreamingStep
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
      .withName(stepName.value)
      .withHadoopJarStep(cfg)
      .withActionOnFailure(ActionOnFailure.CONTINUE)
  }
}

case class EmrStreamingStep(
    override val stepName: StepName,
    mapper: S3Path,
    reducer: Option[S3Path],
    output: S3Path,
    inputs: S3Path*
) extends Step {

  override def config: StepConfig = {
    val wurt = new StreamingStep()
      .withInputs(inputs.map(_.path): _*)
      .withMapper(mapper.path)
      .withOutput(output.path)

    val zzz = reducer.map { r =>
      wurt.withReducer(r.path)
    } getOrElse wurt

    val omgfu =
      zzz.toHadoopJarStepConfig.withJar("/usr/lib/hadoop/hadoop-streaming.jar")

    new StepConfig()
      .withName(stepName.value)
      .withHadoopJarStep(omgfu)
      .withActionOnFailure(ActionOnFailure.CONTINUE)
  }
}

case class EmrStreamingStep2(
  override val stepName: StepName,
  args: List[String]
) extends Step {
  override def config = {
    val cfg = new HadoopJarStepConfig()
      .withJar("command-runner.jar")
      .withArgs(
        "hadoop-streaming"
      )
      .withArgs(args: _*)

    new StepConfig()
      .withName(stepName.value)
      .withHadoopJarStep(cfg)
      .withActionOnFailure(ActionOnFailure.CONTINUE)
  }
}