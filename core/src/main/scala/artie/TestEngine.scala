package artie

import shapeless._
import scalaj.http._

import scala.util.Random
import scala.concurrent.{Future, ExecutionContext}

object TestEngine {

  type ResponseT = HttpResponse[String]

  final case class ResponseCodeDiff(request: RequestT, text: String) extends Diff {
    def stringify(ind: String) = ind + show(request) + "\n" + ind + text
  }

  final case class ResponseContentDiff(request: RequestT, diffs: Seq[Diff]) extends Diff {
    def stringify(ind: String) = ind + show(request) + "\n" + {
      s"$ind{" + GenericDiffOps.mkString(diffs, ind + "  ") + s"\n$ind}"
    }
  }

  def run[P <: HList, A, H <: HList](rand: Random,
                                     pf: Future[P],
                                     config: TestConfig,
                                     requestGen: Random => P => RequestT,
                                     read: Read[A],
                                     ioEffect: HttpRequest => HttpResponse[String])
                                    (implicit ec: ExecutionContext,
                                              gen: LabelledGeneric.Aux[A, H],
                                              genDiff: Lazy[GenericDiff[H]],
                                              ingoreA: IgnoreFields[A] = IgnoreFields[A]): Future[TestState] = {
    def request(p: P): Future[(RequestT, ResponseT, ResponseT)] =
      Future {
        val request    = requestGen(rand)(p)
        val base       = ioEffect(toHttpRequest(config.base, request))
        val refactored = ioEffect(toHttpRequest(config.refactored, request))

        (request, base, refactored)
      }

    // stack-safe
    def engine(p: P, state: TestState): Future[TestState] = {
      import config.{repetitions, parallelism, stopOnFailure}

      val diff = repetitions - state.total

      if (diff > 0 && (!stopOnFailure || state.failed == 0)) {
        Future.sequence(
          Seq.tabulate(if (diff < parallelism) diff else parallelism) { _ =>
            request(p)
          }
        ).flatMap { responses =>
          val newState = responses.foldLeft(state) { case (accState, (request, base, refactored)) =>
            compareResponses(request, base, refactored, read, accState, config.diffLimit)
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

  private[artie] def compareResponses[A, H <: HList](request: RequestT,
                                                     base: HttpResponse[String],
                                                     refactored: HttpResponse[String],
                                                     read: Read[A],
                                                     state: TestState,
                                                     diffLimit: Int)
                                                    (implicit gen: LabelledGeneric.Aux[A, H],
                                                              genDiff: Lazy[GenericDiff[H]],
                                                              ignoreA: IgnoreFields[A] = IgnoreFields[A]): TestState = {
    def addReasons(reason: Diff): Seq[Diff] =
      if (state.reasons.length >= diffLimit)
        state.reasons
      else
        reason +: state.reasons

    if (base.isError && refactored.isError) {
      val reason = ResponseCodeDiff(request, s"invalid:\n  $base\n  $refactored")

      state.copy(invalid = state.invalid + 1, reasons = addReasons(reason))
    }
    else if (base.isError) {
      val reason = ResponseCodeDiff(request, "base service status error:\n  " + base)

      state.copy(failed = state.failed + 1, reasons = addReasons(reason))
    }
    else if (refactored.isError) {
      val reason = ResponseCodeDiff(request, "refactored service status error:\n  " + refactored)

      state.copy(failed = state.failed + 1, reasons = addReasons(reason))
    }
    else {
      val stateE = for {
        baseA <- Either.RightProjection(read(base.body))
        refA  <- Either.RightProjection(read(refactored.body))
      } yield {
        val differences = GenericDiffOps.diff(baseA, refA)

        if (differences.isEmpty)
          state.copy(succeeded = state.succeeded + 1)
        else {
          val reason = ResponseContentDiff(request, differences)

          state.copy(failed = state.failed + 1, reasons = addReasons(reason))
        }
      }

      stateE match {
        case Right(newState) => newState
        case Left(error)     => throw new IllegalArgumentException("couldn't parse response:\n" + error)
      }
    }
  }

  private[artie] def toHttpRequest(baseUri: String, request: RequestT): HttpRequest = request match {
    case (Get, uri, params, headers, _) => Http(baseUri + uri).params(params).headers(headers).method("GET")

    case (Put, uri, params, headers, contentO) =>
      val base = Http(baseUri + uri).params(params).headers(headers)

      contentO.fold(base.method("Pust"))(base.put(_))

    case (Post, uri, params, headers, contentO) =>
      val base = Http(baseUri + uri).params(params).headers(headers)

      contentO.fold(base.method("POST"))(base.postData(_))

    case (Delete, uri, params, headers, _) => Http(baseUri + uri).params(params).headers(headers).method("DELETE")
  }
}
