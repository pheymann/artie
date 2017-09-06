package artie

import shapeless._
import shapeless.labelled.FieldType
import shapeless.record._

trait Diff {

  def stringify(ind: String): String
}

trait GenericDiff[H <: HList] {

  type HI = H
  
  def apply(l: HI, r: HI): Seq[Diff]
}

final case class FieldDiff(key: String, left: Any, right: Any) extends Diff {
  def stringify(ind: String) = s"$ind$key: $left != $right"
}

trait LowPriorityGenericDiff {

  implicit val hnilGenDiff: GenericDiff[HNil] = new GenericDiff[HNil] {
    def apply(l: HNil, r: HNil): Seq[Diff] = Seq.empty
  }

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

final case class ClassDiff(name: String, fieldDiffs: Seq[Diff]) extends Diff {
  def stringify(ind: String) = s"$ind$name {" + fieldDiffs.foldLeft("") { (acc, diff) =>
    s"$acc\n" + diff.stringify(ind + "  ")
  } + s"\n$ind}"
}

trait MediumPriorityGenericDiff extends LowPriorityGenericDiff {
  
  implicit def nestedGenDiff[K <: Symbol, V, R <: HList, T <: HList](implicit wit: Witness.Aux[K],
    gen: LabelledGeneric.Aux[V, R],
    genDiffH: Lazy[GenericDiff[R]],
    genDiffT: Lazy[GenericDiff[T]],
    comp: Compare[V] = Compare.default[V]): GenericDiff[FieldType[K, V] :: T] = new GenericDiff[FieldType[K, V] :: T] {
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

final case class TotalDiff(diffs: Seq[Diff]) extends Diff {
  def stringify(ind: String) = s"$ind{" + GenericDiffOps.mkString(diffs, ind + "  ") + s"\n$ind}"
}

final case class SeqDiff(name: String, elements: Seq[Diff]) extends Diff {
  def stringify(ind: String) = s"$ind$name: [" + elements.foldLeft("") { (acc, diff) =>
    s"$acc\n" + diff.stringify(ind + "  ") + ","
  } + s"\n$ind]"
}

final case class MapDiff(name: String, keyValues: Seq[(String, Diff)]) extends Diff {
  def stringify(ind: String) = s"$ind$name: {" + keyValues.foldLeft("") { case (acc, (key, diffs)) =>
    s"$acc\n$ind  $key:\n" + diffs.stringify(ind + "    ")
  } + s"\n$ind}"
}

final case class CollectionSizeDiff(name: String, sizeL: Int, sizeR: Int) extends Diff {
  def stringify(ind: String) = s"$ind$name: size: $sizeL != $sizeR"
}

final case class MissingValue(value: Any) extends Diff {
  def stringify(ind: String) = s"${ind}missing: ${value.toString}"
}

trait HighPriorityGenericDiff extends MediumPriorityGenericDiff {
  
  implicit def seqGenDiff[K <: Symbol, V, R <: HList, T <: HList](implicit wit: Witness.Aux[K],
    gen: LabelledGeneric.Aux[V, R],
    genDiffH: Lazy[GenericDiff[R]],
    genDiffT: Lazy[GenericDiff[T]]): GenericDiff[FieldType[K, Seq[V]] :: T] = new GenericDiff[FieldType[K, Seq[V]] :: T] {
    def apply(l: HI, r: HI): Seq[Diff] = {
      val sizeL = l.head.length
      val sizeR = r.head.length

      val sizeDiff = {
        if (sizeL != sizeR)
          Seq(CollectionSizeDiff(wit.value.name, l.head.length, r.head.length))
        else
          Seq.empty[Diff]
      }

      val missingDiffs = {
        if (sizeL > sizeR) l.head.slice(sizeR, sizeL).map(MissingValue(_))
        else               r.head.slice(sizeL, sizeR).map(MissingValue(_))
      }

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
        sizeDiff ++: (SeqDiff(wit.value.name, allDiffs) +: genDiffT.value(l.tail, r.tail))
    }
  }
  
  implicit def mapGenDiff[K <: Symbol, V, R <: HList, T <: HList](implicit wit: Witness.Aux[K],
    gen: LabelledGeneric.Aux[V, R],
    genDiffH: Lazy[GenericDiff[R]],
    genDiffT: Lazy[GenericDiff[T]]
  ): GenericDiff[FieldType[K, Map[String, V]] :: T] = new GenericDiff[FieldType[K, Map[String, V]] :: T] {
    def apply(l: HI, r: HI): Seq[Diff] = {
      val sizeDiff = {
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

      val missingDiffs = {
        val diffL: Seq[(String, Diff)] = missingKeysL.map(key => key -> MissingValue(l.head(key)))(collection.breakOut)
        val diffR: Seq[(String, Diff)] = missingKeysR.map(key => key -> MissingValue(r.head(key)))(collection.breakOut)

        diffL ++: diffR
      }

      val diffs: Seq[(String, Diff)] = (l.head.keys.toSet -- missingKeysL)
        .flatMap { key =>
          val valueL = l.head(key)

          r.head
            .get(key)
            .map { valueR =>
              val innerDiffs = genDiffH.value(gen.to(valueL), gen.to(valueR))
              
              if (innerDiffs.isEmpty) None
              else                    Some(key -> TotalDiff(innerDiffs))
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
