package examples

import artie.RefactoringSuite

object ExampleSuite extends RefactoringSuite {

  val specs = StaticProviderSpec ::
              RandomProviderSpec ::
              DatabaseProviderSpec ::
              IgnoreFieldsSpec ::
              Nil
}
