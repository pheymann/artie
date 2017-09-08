package artie.instances

import artie.RandomGenerator

trait RandomInstances {

  import RandomGenerator._

  implicit val randomIntToInt: Rand[Int, Int] = rand => max => rand.nextInt(max)
  implicit val randomToDouble: Rand[Unit, Double] = rand => _ => rand.nextDouble
  implicit val randomToBoolean: Rand[Unit, Boolean] = rand => _ => rand.nextBoolean
}

trait RandomGeneratorInstances {

  type GenRand = Unit => Double

  implicit val randIntGen = new RandomGenerator[Int] {
    override def apply(min: Int, max: Int, rand: GenRand): Int = ((max - min) * rand(())).toInt + min
  }

  implicit val randLongGen = new RandomGenerator[Long] {
    override def apply(min: Long, max: Long, rand: GenRand): Long = ((max - min) * rand(())).toLong + min
  }

  implicit val randFloatGen = new RandomGenerator[Float] {
    override def apply(min: Float, max: Float, rand: GenRand): Float = ((max - min) * rand(()).toFloat) + min
  }

  implicit val randDoubleGen = new RandomGenerator[Double] {
    override def apply(min: Double, max: Double, rand: GenRand): Double = ((max - min) * rand(())) + min
  }
}
