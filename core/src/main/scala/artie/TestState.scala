package artie

final case class TestState(succeeded: Int, invalid: Int, failed: Int, reasons: Seq[Diff] = Nil) {

  val total     = succeeded + invalid + failed
  val isFailed  = failed > 0
  val isSuccess = !isFailed
}

trait TestStateOps {

  implicit class ImplicitTestStateOps(state: TestState) {

    def <+>(other: TestState): TestState = TestState(
      state.succeeded + other.succeeded,
      state.invalid + other.invalid,
      state.failed + other.failed,
      state.reasons ++ other.reasons
    )
  }
}
