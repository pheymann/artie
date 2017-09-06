package artie.instances

import artie.implicits._

import org.specs2.mutable.Specification

final class RandomGeneratorInstancesSpec extends Specification {

  "RandomGenerator instances" >> {
    "generate single random values" >> {
      randIntGen(0, 10, _ => 0.5) === 5
      randLongGen(-10, 0, _ => 0.0) === -10
      randFloatGen(-5f, 5f, _ => 1.0) === 5f
      randDoubleGen(0.0, 10.0, _ => 0.85) === 8.5
    }
  }
}
