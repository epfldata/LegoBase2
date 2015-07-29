package ch.epfl.data
package dblab.legobase
package optimization
package monad

import schema._
import scala.language.implicitConversions
import sc.pardis.ir._
import reflect.runtime.universe.{ TypeTag, Type }
import sc.pardis.optimization._
import deep._
import sc.pardis.types._
import sc.pardis.types.PardisTypeImplicits._
import sc.pardis.shallow.utils.DefaultValue

/**
 * Lowers query monad operations.
 */
class QueryMonadLowering(val schema: Schema, override val IR: LegoBaseExp) extends RuleBasedTransformer[LegoBaseExp](IR) with StructCollector[LegoBaseExp] {
  import IR._

  def array_filter[T: TypeRep](array: Rep[Array[T]], p: Rep[T => Boolean]): Rep[Array[T]] = {
    val Def(Lambda(pred, _, _)) = p
    val size = __newVarNamed[Int](unit(0), "arraySize")
    array_foreach(array, (elem: Rep[T]) => __ifThenElse(pred(elem),
      __assign(size, readVar(size) + unit(1)), unit()))
    val resultSize = readVar(size)
    val resultArray = __newArray[T](resultSize)
    val counter = __newVarNamed[Int](unit(0), "arrayCounter")
    array_foreach(array, (elem: Rep[T]) => {
      __ifThenElse(pred(elem), {
        resultArray(readVar(counter)) = elem
        __assign(counter, readVar(counter) + unit(1))
      },
        unit())

    })
    resultArray
  }

  // def array_dropRight[T: TypeRep](array: Rep[Array[T]], keepNum: Rep[Int]): Rep[Array[T]] = {
  //   val resultSize = keepNum
  //   val resultArray = __newArray[T](resultSize)
  //   Range(unit(0), resultSize).foreach {
  //     __lambda { i =>
  //       resultArray(i) = array(i)
  //     }
  //   }
  //   resultArray
  // }

  def array_map[T: TypeRep, S: TypeRep](array: Rep[Array[T]], f: Rep[T => S]): Rep[Array[S]] = {
    val Def(Lambda(func, _, _)) = f
    val size = __newVarNamed[Int](unit(0), "arraySize")
    val resultArray = __newArray[S](array.length)
    val counter = __newVarNamed[Int](unit(0), "arrayCounter")
    array_foreach(array, (elem: Rep[T]) => {
      resultArray(readVar(counter)) = func(elem)
      __assign(counter, readVar(counter) + unit(1))
    })
    resultArray
  }

  def array_sum[T: TypeRep](array: Rep[Array[T]]): Rep[T] = {
    // TODO handle the generic case using numeric
    assert(typeRep[T] == DoubleType)
    val sumResult = __newVarNamed[Double](unit(0.0), "sumResult")
    array_foreach(array, (elem: Rep[T]) => {
      __assign(sumResult, readVar(sumResult) + elem.asInstanceOf[Rep[Double]])
    })
    readVar(sumResult).asInstanceOf[Rep[T]]
  }

  def array_foldLeft[T: TypeRep, S: TypeRep](array: Rep[Array[T]], z: Rep[S], f: Rep[(S, T) => S]): Rep[S] = {
    val foldResult = __newVarNamed[S](z, "foldResult")
    array_foreach(array, (elem: Rep[T]) => {
      __assign(foldResult, inlineFunction(f, readVar(foldResult), elem))
    })
    readVar(foldResult)
  }

  def array_avg[T: TypeRep](array: Rep[Array[T]]): Rep[T] = {
    // TODO handle the generic case using numeric
    assert(typeRep[T] == DoubleType)
    (array_sum(array).asInstanceOf[Rep[Double]] / array.length).asInstanceOf[Rep[T]]
  }

  rewrite += rule {
    case QueryNew2(array) =>
      array
  }

  rewrite += rule {
    case QueryFilter(monad, p) =>
      val array = apply(monad).asInstanceOf[Rep[Array[Any]]]
      array_filter(array, p)(array.tp.typeArguments(0).asInstanceOf[TypeRep[Any]])
  }

  rewrite += rule {
    case QueryMap(monad, f) =>
      val array = apply(monad).asInstanceOf[Rep[Array[Any]]]
      array_map(array, f)(array.tp.typeArguments(0).asInstanceOf[TypeRep[Any]], f.tp.typeArguments(1).asInstanceOf[TypeRep[Any]])
  }

  rewrite += rule {
    case QueryForeach(monad, f) =>
      val array = apply(monad).asInstanceOf[Rep[Array[Any]]]
      val Def(Lambda(func, _, _)) = f
      array_foreach(array, func)(array.tp.typeArguments(0).asInstanceOf[TypeRep[Any]])
  }

  rewrite += rule {
    case QuerySum(monad) =>
      val array = apply(monad).asInstanceOf[Rep[Array[Any]]]
      array_sum(array)(array.tp.typeArguments(0).asInstanceOf[TypeRep[Any]])
  }

  rewrite += rule {
    case QueryCount(monad) =>
      val array = apply(monad).asInstanceOf[Rep[Array[Any]]]
      array.length
  }

  rewrite += rule {
    case QueryAvg(monad) =>
      val array = apply(monad).asInstanceOf[Rep[Array[Any]]]
      array_avg(array)(array.tp.typeArguments(0).asInstanceOf[TypeRep[Any]])
  }

  rewrite += rule {
    case QueryFoldLeft(monad, z, f) =>
      val array = apply(monad).asInstanceOf[Rep[Array[Any]]]
      array_foldLeft(array, z, f)(array.tp.typeArguments(0).asInstanceOf[TypeRep[Any]], z.tp.asInstanceOf[TypeRep[Any]])
  }

  // rewrite += rule {
  //   case QuerySortBy(monad, f) =>
  //     val array = apply(monad).asInstanceOf[Rep[Array[Any]]]

  // }

  rewrite += statement {
    case sym -> QueryGroupBy(monad, par) => {
      implicit val typeK = par.tp.typeArguments(1).asInstanceOf[TypeRep[Any]]
      implicit val typeV = par.tp.typeArguments(0).asInstanceOf[TypeRep[Any]]
      val result = queryGroupBy(monad, None, par)(typeK, typeV)
      groupByResults(sym) = result
      result.partitionedArray
    }
  }

  rewrite += statement {
    case sym -> QueryFilteredGroupBy(monad, pred, par) => {
      implicit val typeK = par.tp.typeArguments(1).asInstanceOf[TypeRep[Any]]
      implicit val typeV = par.tp.typeArguments(0).asInstanceOf[TypeRep[Any]]
      val result = queryGroupBy(monad, Some(pred), par)(typeK, typeV)
      groupByResults(sym) = result
      result.partitionedArray
    }
  }

  case class GroupByResult[K, V](partitionedArray: Rep[Array[Array[V]]], keyRevertIndex: Rep[Array[K]], eachBucketSize: Rep[Array[Int]], partitions: Rep[Int])
  val groupByResults = scala.collection.mutable.Map[Rep[Any], GroupByResult[Any, Any]]()

  def queryGroupBy[K: TypeRep, V: TypeRep](monad: Rep[Query[V]], pred: Option[Rep[V => Boolean]], par: Rep[V => K]): GroupByResult[K, V] = {
    val originalArray = apply(monad).asInstanceOf[Rep[Array[V]]]
    val MAX_SIZE = unit(4)
    val keyIndex = __newHashMap[K, Int]()
    val keyRevertIndex = __newArray[K](MAX_SIZE)
    val lastIndex = __newVarNamed(unit(0), "lastIndex")
    val array = __newArray[Array[V]](MAX_SIZE)
    // TODO generalize
    schema.stats += "QS_MEM_ARRAY_LINEITEM" -> 4
    schema.stats += "QS_MEM_ARRAY_DOUBLE" -> 4
    val eachBucketSize = __newArray[Int](MAX_SIZE)
    Range(unit(0), MAX_SIZE).foreach {
      __lambda { i =>
        array(i) = __newArray[V](originalArray.length)
        eachBucketSize(i) = unit(0)
      }
    }

    // printf(unit("start!"))
    array_foreach(originalArray, (elem: Rep[V]) => {
      // val key = par(elem)
      val cond = pred.map(p => inlineFunction(p, elem)).getOrElse(unit(true))
      __ifThenElse(cond, {
        val key = inlineFunction(par, elem)
        val bucket = keyIndex.getOrElseUpdate(key, {
          keyRevertIndex(readVar(lastIndex)) = key
          __assign(lastIndex, readVar(lastIndex) + unit(1))
          readVar(lastIndex) - unit(1)
        })
        array(bucket)(eachBucketSize(bucket)) = elem
        eachBucketSize(bucket) += unit(1)
      }, unit())
    })
    GroupByResult(array, keyRevertIndex, eachBucketSize, MAX_SIZE)
  }

  // def queryGroupByMapValues[K: TypeRep, V: TypeRep, S: TypeRep](monad: Rep[Query[V]], pred: Option[Rep[V => Boolean]], par: Rep[V => K], func: Rep[Array[V] => S]): Rep[Any] = {
  def groupedQueryMapValues[K: TypeRep, V: TypeRep, S: TypeRep](groupedMonad: Rep[GroupedQuery[K, V]], func: Rep[Array[V] => S]): Rep[Any] = {
    val GroupByResult(array, keyRevertIndex, eachBucketSize, partitions) =
      // queryGroupBy(monad, pred, par)
      groupByResults(groupedMonad.asInstanceOf[Rep[Any]]).asInstanceOf[GroupByResult[K, V]]
    val resultArray = __newArray[(K, S)](partitions)
    Range(unit(0), array.length).foreach {
      __lambda { i =>
        // val arr = array_dropRight(array(i), eachBucketSize(i))
        val arr = array(i).dropRight(array(i).length - eachBucketSize(i))
        // System.out.println(s"arr size ${arr.size} bucket size ${eachBucketSize(i)}")
        val key = keyRevertIndex(i)
        val newValue = inlineFunction(func, arr)
        resultArray(i) = Tuple2(key, newValue)
      }
    }
    resultArray.asInstanceOf[Rep[Any]]
  }

  // rewrite += rule {
  //   case GroupedQueryMapValues(Def(QueryGroupBy(monad, par)), func) =>
  //     implicit val typeK = par.tp.typeArguments(1).asInstanceOf[TypeRep[Any]]
  //     implicit val typeV = par.tp.typeArguments(0).asInstanceOf[TypeRep[Any]]
  //     implicit val typeS = func.tp.typeArguments(1).asInstanceOf[TypeRep[Any]]
  //     queryGroupByMapValues(monad, None, par, func.asInstanceOf[Rep[Array[Any] => Any]])(typeK, typeV, typeS)

  // }

  // rewrite += rule {
  //   case GroupedQueryMapValues(Def(QueryFilteredGroupBy(monad, pred, par)), func) =>
  //     implicit val typeK = par.tp.typeArguments(1).asInstanceOf[TypeRep[Any]]
  //     implicit val typeV = par.tp.typeArguments(0).asInstanceOf[TypeRep[Any]]
  //     implicit val typeS = func.tp.typeArguments(1).asInstanceOf[TypeRep[Any]]
  //     queryGroupByMapValues(monad, Some(pred), par, func.asInstanceOf[Rep[Array[Any] => Any]])(typeK, typeV, typeS)

  // }

  rewrite += rule {
    case GroupedQueryMapValues(groupedMonad, func) =>
      implicit val typeK = groupedMonad.tp.typeArguments(0).asInstanceOf[TypeRep[Any]]
      implicit val typeV = groupedMonad.tp.typeArguments(1).asInstanceOf[TypeRep[Any]]
      implicit val typeS = func.tp.typeArguments(1).asInstanceOf[TypeRep[Any]]
      // queryGroupByMapValues(monad, Some(pred), par, func.asInstanceOf[Rep[Array[Any] => Any]])(typeK, typeV, typeS)
      groupedQueryMapValues(groupedMonad, func.asInstanceOf[Rep[Array[Any] => Any]])(typeK, typeV, typeS)

  }

  def array_sortBy[T: TypeRep, S: TypeRep](array: Rep[Array[T]], sortFunction: Rep[T => S]): Rep[Array[T]] = {
    val resultArray = __newArray[T](array.length)
    val counter = __newVarNamed[Int](unit(0), "arrayCounter")
    // TODO generalize
    val treeSet = __newTreeSet2(Ordering[T](__lambda { (x, y) =>
      inlineFunction(sortFunction, x).asInstanceOf[Rep[Int]] - inlineFunction(sortFunction, y).asInstanceOf[Rep[Int]]
    }))
    array_foreach(array, (elem: Rep[T]) => {
      treeSet += elem
      __assign(counter, readVar(counter) + unit(1))
    })
    Range(unit(0), array.length).foreach(__lambda { i =>
      val elem = treeSet.head
      treeSet -= elem
      resultArray(i) = elem
    })
    resultArray
  }

  rewrite += statement {
    case sym -> QuerySortBy(monad, sortFunction) => {
      val array = apply(monad).asInstanceOf[Rep[Array[Any]]]
      array_sortBy(array, sortFunction)(array.tp.typeArguments(0).asInstanceOf[TypeRep[Any]], sortFunction.tp.typeArguments(1).asInstanceOf[TypeRep[Any]])
    }
  }

  rewrite += rule {
    case JoinableQueryNew(joinMonad) => apply(joinMonad)
  }

  rewrite += rule {
    case QueryGetList(monad) => apply(monad)
  }

  def concat_types[T: TypeRep, S: TypeRep, Res: TypeRep]: TypeRep[Res] = {
    val leftTag = typeRep[T].asInstanceOf[RecordType[T]].tag
    val rightTag = typeRep[S].asInstanceOf[RecordType[S]].tag
    val concatTag = StructTags.CompositeTag[T, S]("", "", leftTag, rightTag)
    new RecordType(concatTag, Some(typeRep[Res].asInstanceOf[TypeRep[Any]])).asInstanceOf[TypeRep[Res]]
  }

  def concat_records[T: TypeRep, S: TypeRep, Res: TypeRep](elem1: Rep[T], elem2: Rep[S]): Rep[Res] = {
    val resultType = concat_types[T, S, Res]
    val elems1 = getStructDef(typeRep[T]).get.fields.map(x => PardisStructArg(x.name, x.mutable, field(elem1, x.name)(x.tpe)))
    val elems2 = getStructDef(typeRep[S]).get.fields.map(x => PardisStructArg(x.name, x.mutable, field(elem2, x.name)(x.tpe)))
    // val structFields = elems.zip(elemsRhs).map(x => PardisStructArg(x._1.name, x._1.mutable, field(x._2.rec, x._2.name)(x._2.tp)))
    val structFields = elems1 ++ elems2
    struct(structFields: _*)(resultType)
  }

  def hashJoin[T: TypeRep, S: TypeRep, R: TypeRep, Res: TypeRep](array1: Rep[Array[T]], array2: Rep[Array[S]], leftHash: Rep[T => R], rightHash: Rep[S => R], joinCond: Rep[(T, S) => Boolean]): Rep[Array[Res]] = {
    // TODO generalize
    val maxSize = unit(1000000)
    val res = __newArray[Res](maxSize)(concat_types[T, S, Res])
    val counter = __newVar[Int](unit(0))
    val hm = __newMultiMap[R, T]()
    // System.out.println(concat_types[T, S, Res])
    array_foreach(array1, (elem: Rep[T]) => {
      hm.addBinding(leftHash(elem), elem)
    })
    array_foreach(array2, (elem: Rep[S]) => {
      val k = rightHash(elem)
      hm.get(k) foreach {
        __lambda { tmpBuffer =>
          tmpBuffer foreach {
            __lambda { bufElem =>
              __ifThenElse(joinCond(bufElem, elem), {
                res(readVar(counter)) = //bufElem.asInstanceOf[Rep[Record]].concatenateDynamic(elem.asInstanceOf[Rep[Record]])(elem.tp.asInstanceOf[TypeRep[Record]]).asInstanceOf[Rep[Res]]
                  concat_records[T, S, Res](bufElem, elem)
                __assign(counter, readVar(counter) + unit(1))
              }, unit())
            }
          }
        }
      }
    })
    res.dropRight(maxSize - readVar(counter))
  }

  rewrite += statement {
    case sym -> JoinableQueryJoin(monad1, monad2, leftHash, rightHash, joinCond) => {
      val arr1 = apply(monad1).asInstanceOf[Rep[Array[Any]]]
      val arr2 = apply(monad2).asInstanceOf[Rep[Array[Any]]]
      hashJoin(arr1,
        arr2,
        leftHash.asInstanceOf[Rep[Any => Any]],
        rightHash.asInstanceOf[Rep[Any => Any]],
        joinCond.asInstanceOf[Rep[(Any, Any) => Boolean]])(arr1.tp.typeArguments(0).asInstanceOf[TypeRep[Any]],
          arr2.tp.typeArguments(0).asInstanceOf[TypeRep[Any]],
          leftHash.tp.typeArguments(1).asInstanceOf[TypeRep[Any]],
          sym.tp.typeArguments(0).asInstanceOf[TypeRep[Any]])
      // System.out.println(unit(s"$leftHash, $rightHash, $joinCond"))
    }
  }
}
