package artie

import shapeless._
import scalaj.http._

import scala.util.Random
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.FiniteDuration

import java.net.SocketTimeoutException

object TestEngine {

  type ResponseT = HttpResponse[String]

  final case class RequestTimeoutDiff(request: RequestT, msg: String) extends Diff {
    def stringify(ind: String) = ind + show(request) + "\nrequest timeout: " + msg
  }

  final case class ResponseCodeDiff(request: RequestT, text: String) extends Diff {
    def stringify(ind: String) = ind + show(request) + "\n" + ind + text
  }

  final case class ResponseContentDiff(request: RequestT, diffs: Seq[Diff]) extends Diff {
    def stringify(ind: String) = ind + show(request) + "\n" + {
      s"$ind{" + GenericDiffOps.mkString(diffs, ind + "  ") + s"\n$ind}"
    }
  }

  // executes a test case
  def run[P <: HList, A](rand: Random,
                         pf: Future[P],
                         config: TestConfig,
                         requestGen: Random => P => RequestT,
                         read: Read[A],
                         ioEffect: HttpRequest => HttpResponse[String])
                        (implicit ec: ExecutionContext,
                                  diff: GenericDiffRunner[A]): Future[TestState] = {
    // build and execute a single request and collect responses
    def request(p: P): Future[(RequestT, Either[Diff, (ResponseT, ResponseT)])] = {
      val request = requestGen(rand)(p)

      Future {
        val base       = ioEffect(toHttpRequest(config.base, request, config.requestTimeout))
        val refactored = ioEffect(toHttpRequest(config.refactored, request, config.requestTimeout))

        (request, Right((base, refactored)))
      }.recover {
        case cause: SocketTimeoutException => (request, Left(RequestTimeoutDiff(request, cause.getMessage())))
        case other                         => throw other
      }
    }

    // stack-safe
    def engine(p: P, state: TestState): Future[TestState] = {
      import config.{repetitions, parallelism, stopOnFailure}

      val remaining = repetitions - state.total

      // if we have remaining repetitions and we didn't see a failure yet
      if (remaining > 0 && (!stopOnFailure || state.failed == 0)) {
        Future.sequence(
          Seq.tabulate(if (remaining < parallelism) remaining else parallelism) { _ =>
            request(p)
          }
        ).flatMap { responses =>
          val newState = responses.foldLeft(state) { 
            case (accState, (request, Right((base, refactored)))) => compareResponses(request, base, refactored, read, accState, config.diffLimit)
            case (accState, (request, Left(errorDiff)))           => accState.copy(failed = accState.failed + 1, reasons = addReasons(accState, config.diffLimit, Seq(errorDiff)))
          }

          if (config.showProgress)
            print(s"\r   processed: ${newState.total} / ${config.repetitions}")

          engine(p, newState)
        }
      }
      else
        Future.successful(state)
    }

    pf.flatMap(engine(_, TestState(0, 0, 0))).andThen { case _ =>      
      if (config.showProgress)
        print("\n")
    }
  }

  private def addReasons(state: TestState, diffLimit: Int, reasons: Seq[Diff]): Seq[Diff] =
    if (state.reasons.length >= diffLimit)
      state.reasons
    else
      reasons ++: state.reasons

  private[artie] def compareResponses[A](request: RequestT,
                                         base: HttpResponse[String],
                                         refactored: HttpResponse[String],
                                         read: Read[A],
                                         state: TestState,
                                         diffLimit: Int)
                                        (implicit diff: GenericDiffRunner[A]): TestState = {
    if (base.isError && refactored.isError) {
      if (base.code == refactored.code) {
        val reason = ResponseCodeDiff(request, s"invalid:\n  $base\n  $refactored")

        state.copy(invalid = state.invalid + 1, reasons = addReasons(state, diffLimit, Seq(reason)))
      }
      else {
        val baseReason   = ResponseCodeDiff(request, "base service status error:\n  " + base)
        val refactReason = ResponseCodeDiff(request, "refactored service status error:\n  " + refactored)

        state.copy(failed = state.failed + 1, reasons = addReasons(state, diffLimit, Seq(baseReason, refactReason)))
      }
    }
    else if (base.isError) {
      val reason = ResponseCodeDiff(request, "base service status error:\n  " + base)

      state.copy(failed = state.failed + 1, reasons = addReasons(state, diffLimit, Seq(reason)))
    }
    else if (refactored.isError) {
      val reason = ResponseCodeDiff(request, "refactored service status error:\n  " + refactored)

      state.copy(failed = state.failed + 1, reasons = addReasons(state, diffLimit, Seq(reason)))
    }
    else {
      val stateE = for {
        baseA <- Either.RightProjection(read(base.body))
        refA  <- Either.RightProjection(read(refactored.body))
      } yield {
        val differences = diff(baseA, refA)

        if (differences.isEmpty)
          state.copy(succeeded = state.succeeded + 1)
        else {
          val reason = ResponseContentDiff(request, differences)

          state.copy(failed = state.failed + 1, reasons = addReasons(state, diffLimit, Seq(reason)))
        }
      }

      stateE match {
        case Right(newState) => newState
        case Left(error)     => throw new IllegalArgumentException(s"couldn't parse response:\n request: ${request}\n response: ${base}\n $error")
      }
    }
  }

  private[artie] def toHttpRequest(baseUri: String, request: RequestT, timeout: FiniteDuration): HttpRequest = request match {
    case (Get, uri, params, headers, _) => Http(baseUri + uri).params(params).headers(headers).option(HttpOptions.readTimeout(timeout.toMillis.toInt)).method("GET")

    case (Put, uri, params, headers, contentO) =>
      val base = Http(baseUri + uri).params(params).headers(headers).option(HttpOptions.readTimeout(timeout.toMillis.toInt))

      contentO.fold(base.method("Pust"))(base.put(_))

    case (Post, uri, params, headers, contentO) =>
      val base = Http(baseUri + uri).params(params).headers(headers).option(HttpOptions.readTimeout(timeout.toMillis.toInt))

      contentO.fold(base.method("POST"))(base.postData(_))

    case (Delete, uri, params, headers, _) => Http(baseUri + uri).params(params).headers(headers).option(HttpOptions.readTimeout(timeout.toMillis.toInt)).method("DELETE")
  }
}
