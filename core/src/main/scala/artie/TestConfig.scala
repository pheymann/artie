package artie

final case class TestConfig(baseHost: String,
                            basePort: Int,
                            refactoredHost: String,
                            refactoredPort: Int,
                            repetitions: Int,
                            parallelism: Int,
                            stopOnFailure: Boolean,
                            diffLimit: Int,
                            showProgress: Boolean) {

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
    diffLimit     = 1,
    showProgress  = true
  )

  implicit class ImplicitTestConfigOps(config: TestConfig) {

    /** Number of repetitions of a test case.
      * 
      * @rep number of repetitions
      */
    def repetitions(rep: Int) = config.copy(repetitions = rep)

    /** Number of parallel running requests.
      * 
      * @par number of parallel requests.
      */
    def parallelism(par: Int) = config.copy(parallelism = par)

    /** Should a test case stop when it encounters the first failure?
      * 
      *  @stop if `true` a test case will be stopped on first failure.
      */
    def stopOnFailure(stop: Boolean) = config.copy(stopOnFailure = stop)

    /** Maximum number of shown response differences.
      * 
      * @limit shown diff limit
      */
    def shownDiffsLimit(limit: Int) = config.copy(diffLimit = limit)
  }
}
