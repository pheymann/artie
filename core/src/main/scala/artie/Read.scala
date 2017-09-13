package artie

/** Type class to map raw jsons to instances of `A`. */
trait Read[A] extends (String => Either[String, A])
