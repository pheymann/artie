package artie

import shapeless._
import shapeless.labelled.FieldType

/** Difference if a field `a` of two instances of some case class `A`.*/
trait Diff {

  /** Differences to String */
  def stringify(ind: String): String
}

/** Function generating the `Diff`s of between two LabelledGenerics. */
trait GenericDiff[H <: HList] {

  type HI = H
  
  def apply(l: HI, r: HI): Seq[Diff]
}

/** Single field difference. */
final case class FieldDiff(key: String, left: Any, right: Any) extends Diff {
  def stringify(ind: String) = s"$ind$key: $left != $right"
}

trait LowPriorityGenericDiff {

  // case: end of list
  implicit val hnilGenDiff: GenericDiff[HNil] = new GenericDiff[HNil] {
    def apply(l: HNil, r: HNil): Seq[Diff] = Seq.empty
  }

  // case: LabelledGeneric without nestings
  implicit def basicGenDiff[K <: Symbol, V, T <: HList](implicit wit: Witness.Aux[K],
                                                                 genDiff: Lazy[GenericDiff[T]],
                                                                 equ: Eq[V] = Eq.default[V]): GenericDiff[FieldType[K, V] :: T] =
    new GenericDiff[FieldType[K, V] :: T] {
      def apply(l: HI, r: HI): Seq[Diff] =
        if (equ.not(l.head, r.head))
          FieldDiff(wit.value.name, l.head, r.head) +: genDiff.value(l.tail, r.tail)
        else
          genDiff.value(l.tail, r.tail)
    }
}

/** Collection of differences of a case class instance. */
final case class ClassDiff(name: String, fieldDiffs: Seq[Diff]) extends Diff {
  def stringify(ind: String) = s"$ind$name {" + fieldDiffs.foldLeft("") { (acc, diff) =>
    s"$acc\n" + diff.stringify(ind + "  ")
  } + s"\n$ind}"
}

trait MediumPriorityGenericDiff extends LowPriorityGenericDiff {

  // case: nested LabelledGeneric (case class in case class)
  //  - override comparison with `Compare[V]` instances if needed
  implicit def nestedGenDiff[K <: Symbol, V, R <: HList, T <: HList](implicit wit: Witness.Aux[K],
                                                                              gen: LabelledGeneric.Aux[V, R],
                                                                              genDiffH: Lazy[GenericDiff[R]],
                                                                              genDiffT: Lazy[GenericDiff[T]],
                                                                              comp: Compare[V] = Compare.default[V])
      : GenericDiff[FieldType[K, V] :: T] = new GenericDiff[FieldType[K, V] :: T] {
    def apply(l: HI, r: HI): Seq[Diff] = {
      val diffs = {
        if (comp.active) comp(l.head, r.head)
        else             genDiffH.value(gen.to(l.head), gen.to(r.head))
      }
      
      if (diffs.isEmpty) genDiffT.value(l.tail, r.tail)
      else               ClassDiff(wit.value.name, diffs) +: genDiffT.value(l.tail, r.tail)
    }
  }
}

/** Collection of multiple `Diff` types. */
final case class TotalDiff(diffs: Seq[Diff]) extends Diff {
  def stringify(ind: String) = s"$ind{" + GenericDiffOps.mkString(diffs, ind + "  ") + s"\n$ind}"
}

/** Difference betwenn collection sizes. */
final case class CollectionSizeDiff(name: String, sizeL: Int, sizeR: Int) extends Diff {
  def stringify(ind: String) = s"$ind$name: size: $sizeL != $sizeR"
}

/** Difference of `Set` elements */
final case class CollectionElementsDiff(name: String, elements: Seq[Diff]) extends Diff {
  def stringify(ind: String) = s"$ind$name: [" + elements.foldLeft("") { (acc, diff) =>
    s"$acc\n" + diff.stringify(ind + "  ") + ","
  } + s"\n$ind]"
}

/** Difference of `Map` pairs */
final case class MapDiff(name: String, keyValues: Seq[(String, Diff)]) extends Diff {
  def stringify(ind: String) = s"$ind$name: {" + keyValues.foldLeft("") { case (acc, (key, diffs)) =>
    s"$acc\n$ind  $key:\n" + diffs.stringify(ind + "    ")
  } + s"\n$ind}"
}

/** Value is missing in the other collection. */
final case class MissingValue(value: Any) extends Diff {
  def stringify(ind: String) = s"${ind}missing: ${value.toString}"
}

trait HighPriorityGenericDiff extends MediumPriorityGenericDiff {

  // case: LabelledGenerics in a `Seq`
  //  - compares elements in order
  //  - if the sizes differ it returns also the missing elements
  implicit def seqGenDiff[K <: Symbol, V, R <: HList, T <: HList](implicit wit: Witness.Aux[K],
                                                                           gen: LabelledGeneric.Aux[V, R],
                                                                           genDiffH: Lazy[GenericDiff[R]],
                                                                           genDiffT: Lazy[GenericDiff[T]])
      : GenericDiff[FieldType[K, Seq[V]] :: T] = new GenericDiff[FieldType[K, Seq[V]] :: T] {
    def apply(l: HI, r: HI): Seq[Diff] = {
      val sizeL = l.head.length
      val sizeR = r.head.length

      // compare sizes
      val sizeDiff = {
        if (sizeL != sizeR)
          Seq(CollectionSizeDiff(wit.value.name, l.head.length, r.head.length))
        else
          Seq.empty[Diff]
      }

      // collect missing elements
      val missingDiffs = {
        if (sizeL > sizeR) l.head.slice(sizeR, sizeL).map(MissingValue(_))
        else               r.head.slice(sizeL, sizeR).map(MissingValue(_))
      }

      // generate differences between elements
      val diffs = l.head.zip(r.head)
        .flatMap { case (valueL, valueR) =>
          val diffs = genDiffH.value(gen.to(valueL), gen.to(valueR))

          if (diffs.isEmpty) None
          else               Some(TotalDiff(diffs))
        }

      val allDiffs = diffs ++: missingDiffs

      if (allDiffs.isEmpty)
        sizeDiff ++: genDiffT.value(l.tail, r.tail)
      else
        sizeDiff ++: (CollectionElementsDiff(wit.value.name, allDiffs) +: genDiffT.value(l.tail, r.tail))
    }
  }

  // case: LabelledGenerics in an `Array`
  //  - compares elements in order
  //  - if the sizes differ it returns also the missing elements
  implicit def arrayGenDiff[K <: Symbol, V, R <: HList, T <: HList](implicit wit: Witness.Aux[K],
                                                                             gen: LabelledGeneric.Aux[V, R],
                                                                             genDiffH: Lazy[GenericDiff[R]],
                                                                             genDiffT: Lazy[GenericDiff[T]])
      : GenericDiff[FieldType[K, Array[V]] :: T] = new GenericDiff[FieldType[K, Array[V]] :: T] {
    def apply(l: HI, r: HI): Seq[Diff] = {
      val sizeL = l.head.length
      val sizeR = r.head.length

      // compare sizes
      val sizeDiff = {
        if (sizeL != sizeR)
          Seq(CollectionSizeDiff(wit.value.name, l.head.length, r.head.length))
        else
          Seq.empty[Diff]
      }

      // collect missing elements
      val missingDiffs = {
        if (sizeL > sizeR) l.head.slice(sizeR, sizeL).map(MissingValue(_))
        else               r.head.slice(sizeL, sizeR).map(MissingValue(_))
      }

      // generate differences between elements
      val diffs = l.head.zip(r.head)
        .flatMap { case (valueL, valueR) =>
          val diffs = genDiffH.value(gen.to(valueL), gen.to(valueR))

          if (diffs.isEmpty) None
          else               Some(TotalDiff(diffs))
        }

      val allDiffs = diffs ++: missingDiffs

      if (allDiffs.isEmpty)
        sizeDiff ++: genDiffT.value(l.tail, r.tail)
      else
        sizeDiff ++: (CollectionElementsDiff(wit.value.name, allDiffs) +: genDiffT.value(l.tail, r.tail))
    }
  }

  // case: LabelledGenerics in a `Set`
  //   - if the sizes differ it returns also the missing elements
  //   - can only provide MissingValue diffs; relation betwenn different elements cannot
  //     be drawn
  implicit def setGenDiff[K <: Symbol, V, R <: HList, T <: HList](implicit wit: Witness.Aux[K],
                                                                           genDiffT: Lazy[GenericDiff[T]])
      : GenericDiff[FieldType[K, Set[V]] :: T] = new GenericDiff[FieldType[K, Set[V]] :: T] {
    def apply(l: HI, r: HI): Seq[Diff] = {
      val sizeL = l.head.size
      val sizeR = r.head.size

      // compare sizes
      val sizeDiff = {
        if (sizeL != sizeR)
          Seq(CollectionSizeDiff(wit.value.name, sizeL, sizeR))
        else
          Seq.empty[Diff]
      }

      val missingL = l.head.diff(r.head)
      val missingR = r.head.diff(l.head)

      // collect missing elements
      val missingDiffs = {
        val diffL: Seq[Diff] = missingL.map(el => MissingValue(el))(collection.breakOut)
        val diffR: Seq[Diff] = missingR.map(el => MissingValue(el))(collection.breakOut)

        diffL ++: diffR
      }

      if (missingDiffs.isEmpty)
        sizeDiff ++: genDiffT.value(l.tail, r.tail)
      else
        sizeDiff ++: (CollectionElementsDiff(wit.value.name, missingDiffs) +: genDiffT.value(l.tail, r.tail))
    }
  }

  // case: LabelledGenerics in a `Map`
  //  - compares elements by key
  //  - collects missing key-value pairs
  implicit def mapGenDiff[K <: Symbol, VK, V, R <: HList, T <: HList](implicit wit: Witness.Aux[K],
                                                                               gen: LabelledGeneric.Aux[V, R],
                                                                               genDiffH: Lazy[GenericDiff[R]],
                                                                               genDiffT: Lazy[GenericDiff[T]])
      : GenericDiff[FieldType[K, Map[VK, V]] :: T] = new GenericDiff[FieldType[K, Map[VK, V]] :: T] {
    def apply(l: HI, r: HI): Seq[Diff] = {
      val sizeDiff = {
        // compare sizes
        val sizeL = l.head.size
        val sizeR = r.head.size

        if (sizeL != sizeR)
          Seq(CollectionSizeDiff(wit.value.name, sizeL, sizeR))
        else
          Seq.empty[Diff]
      }

      val keysL = l.head.keys.toSet
      val keysR = r.head.keys.toSet

      val missingKeysL = keysL.diff(keysR)
      val missingKeysR = keysR.diff(keysL)

      // collect missing key-value pairs
      val missingDiffs = {
        val diffL: Seq[(String, Diff)] = missingKeysL.map(key => key.toString -> MissingValue(l.head(key)))(collection.breakOut)
        val diffR: Seq[(String, Diff)] = missingKeysR.map(key => key.toString -> MissingValue(r.head(key)))(collection.breakOut)

        diffL ++: diffR
      }

      // generate differences between remaining elements
      val diffs: Seq[(String, Diff)] = (l.head.keys.toSet -- missingKeysL)
        .flatMap { key =>
          val valueL = l.head(key)

          r.head
            .get(key)
            .map { valueR =>
              val innerDiffs = genDiffH.value(gen.to(valueL), gen.to(valueR))
              
              if (innerDiffs.isEmpty) None
              else                    Some(key.toString -> TotalDiff(innerDiffs))
            }
            .getOrElse(None)
        }(collection.breakOut)

      val allDiffs = diffs ++: missingDiffs

      if (allDiffs.isEmpty)
        sizeDiff ++: genDiffT.value(l.tail, r.tail)
      else
        sizeDiff ++: (MapDiff(wit.value.name, allDiffs) +: genDiffT.value(l.tail, r.tail))
    }
  }
}

final case class TextDiff(text: String) extends Diff {
  def stringify(ind: String) = ind + text
}

object GenericDiffOps {

  def diff[A, H <: HList](l: A, r: A)(implicit gen: LabelledGeneric.Aux[A, H],
                                               genDiff: Lazy[GenericDiff[H]],
                                               comp: Compare[A] = Compare.default[A]): Seq[Diff] =
    if (comp.active) comp(l, r)
    else             genDiff.value(gen.to(l), gen.to(r))

  private[artie] def mkString(diffs: Seq[Diff], ind: String): String =
    diffs.foldLeft("") { (acc, diff) =>
      s"$acc\n" + diff.stringify(ind)
    }

  def mkString(diffs: Seq[Diff]): String =
    diffs.foldLeft("") { (acc, diff) =>
      s"$acc\n" + diff.stringify("") + "\n"
    }
}
