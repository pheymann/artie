package artie

final case class TestConfig(baseHost: String,
                            basePort: Int,
                            refactoredHost: String,
                            refactoredPort: Int,
                            repetitions: Int,
                            parallelism: Int,
                            stopOnFailure: Boolean,
                            diffLimit: Int) {

  val base       = s"$baseHost:$basePort"
  val refactored = s"$refactoredHost:$refactoredPort"
}

trait TestConfigOps {

  def Config(baseHost: String, basePort: Int, refactoredHost: String, refactoredPort: Int) = TestConfig(
    baseHost,
    basePort,
    refactoredHost,
    refactoredPort,
    repetitions   = 1,
    parallelism   = 1,
    stopOnFailure = true,
    diffLimit     = 1
  )

  implicit class ImplicitTestConfigOps(config: TestConfig) {

    def repetitions(rep: Int) = config.copy(repetitions = rep)
    def parallelism(par: Int) = config.copy(parallelism = par)
    def stopOnFailure(stop: Boolean) = config.copy(stopOnFailure = stop)
    def shownDiffsLimit(limit: Int) = config.copy(diffLimit = limit)
  }
}
