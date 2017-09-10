package examples

import artie.suite.RefactoringSuite

object ExampleSuite extends RefactoringSuite {

  val specs = StaticProviderSpec ::
              RandomProviderSpec ::
              DatabaseProviderSpec ::
              IgnoreFieldsSpec ::
              Nil
}
