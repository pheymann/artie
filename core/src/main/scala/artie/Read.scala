package artie

/** Reads a Json String and creates and instance of `A` or failes. */
trait Read[A] extends (String => Either[String, A])
