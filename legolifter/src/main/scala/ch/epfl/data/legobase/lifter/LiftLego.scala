package ch.epfl.data
package legobase
package lifter

import scala.reflect.runtime.universe
import universe.typeOf

import queryengine.volcano._
import ch.epfl.data.autolifter._
import ch.epfl.data.autolifter.annotations._
import ch.epfl.data.autolifter.annotations.Custom._
import java.util.{ Calendar, GregorianCalendar }
import scala.collection.mutable.MirrorHashMap
import scala.collection.mutable.MirrorSet
import scala.collection.mutable.MirrorTreeSet
import scala.collection.mutable.MirrorDefaultEntry
import scala.collection.mutable.MirrorArrayBuffer

object LiftLego {
  val reportToFile = true

  def main(args: Array[String]) {
    implicit val al = new AutoLifter(universe)
    generateLegoBase
    generateNumber
    generateTpe[MirrorArray[_]]
    generateCollection
  }

  val folder = "legocompiler/src/main/scala/ch/epfl/data/legobase/deep"

  def generateNumber(implicit al: AutoLifter) {
    val liftedCodes = List(
      // TODO should be ported to pardis
      // al.autoLift[MirrorInt],
      al.autoLift[MirrorDouble],
      al.autoLift[MirrorCharacter],
      al.autoLift[MirrorLong],
      al.autoLift[MirrorInteger] // TODO should be ported to pardis
      // , al.autoLift[MirrorBoolean]
      )
    val liftedCode = liftedCodes.mkString("\n")
    val file = "DeepScalaNumber"
    printToFile(new java.io.File(s"$folder/scalalib/$file.scala")) { pw =>
      pw.println(s"""/* Generated by AutoLifter © ${new GregorianCalendar().get(Calendar.YEAR)} */

package ch.epfl.data
package legobase
package deep
package scalalib

import pardis.ir._
import pardis.ir.pardisTypeImplicits._

$liftedCode
""")
    }
  }

  def generateLegoBase(implicit al: AutoLifter) {
    val liftedCodes = List(
      al.autoLift[queryengine.GroupByClass],
      al.autoLift[queryengine.Q3GRPRecord],
      al.autoLift[queryengine.AGGRecord[Any]],
      al.autoLift[storagemanager.TPCHRelations.LINEITEMRecord],
      al.autoLift[storagemanager.TPCHRelations.SUPPLIERRecord],
      al.autoLift[storagemanager.TPCHRelations.PARTSUPPRecord],
      al.autoLift[storagemanager.TPCHRelations.REGIONRecord],
      al.autoLift[storagemanager.TPCHRelations.NATIONRecord],
      al.autoLift[storagemanager.TPCHRelations.PARTRecord],
      al.autoLift[storagemanager.TPCHRelations.CUSTOMERRecord],
      al.autoLift[storagemanager.TPCHRelations.ORDERSRecord],
      al.autoLift[pardis.shallow.OptimalString],
      al.autoLift[storagemanager.K2DBScanner](Custom(component = "DeepDSL", excludedFields = List(CMethod("br"), CMethod("sdf")))),
      al.autoLift[queryengine.WindowRecord[Any, Any]])
    val liftedCode = liftedCodes.mkString("\n")
    val file = "DeepLegoBase"
    printToFile(new java.io.File(s"$folder/$file.scala")) { pw =>
      pw.println(s"""/* Generated by AutoLifter © ${new GregorianCalendar().get(Calendar.YEAR)} */

package ch.epfl.data
package legobase
package deep

import scalalib._
import pardis.ir._
import pardis.ir.pardisTypeImplicits._
import pardis.deep.scalalib._

$liftedCode
trait DeepDSL extends OperatorsComponent with AGGRecordComponent with WindowRecordComponent with CharacterComponent 
  with DoubleComponent with IntComponent with LongComponent with ArrayComponent 
  with GroupByClassComponent
  with Q3GRPRecordComponent
  with LINEITEMRecordComponent
  with SUPPLIERRecordComponent
  with PARTSUPPRecordComponent
  with REGIONRecordComponent
  with NATIONRecordComponent
  with PARTRecordComponent
  with CUSTOMERRecordComponent
  with ORDERSRecordComponent
  with OptimalStringComponent
  with K2DBScannerComponent with IntegerComponent 
  with BooleanComponent with HashMapComponent with SetComponent with TreeSetComponent 
  with DefaultEntryComponent with ArrayBufferComponent with ManualLiftedLegoBase
""")
    }
  }

  def generateCollection(implicit al: AutoLifter) {
    val liftedCodes = List(
      al.autoLift[MirrorHashMap[Any, Any]](Custom("DeepDSL")),
      al.autoLift[MirrorSet[Any]](Custom("DeepDSL")),
      al.autoLift[MirrorTreeSet[Any]](Custom("DeepDSL")),
      al.autoLift[MirrorDefaultEntry[Any, Any]](Custom("DeepDSL")),
      al.autoLift[MirrorArrayBuffer[Any]](Custom("DeepDSL")))
    val liftedCode = liftedCodes.mkString("\n")
    val file = "DeepScalaCollection"
    printToFile(new java.io.File(s"$folder/scalalib/$file.scala")) { pw =>
      pw.println(s"""/* Generated by AutoLifter © ${new GregorianCalendar().get(Calendar.YEAR)} */

package ch.epfl.data
package legobase
package deep
package scalalib

import pardis.ir._
import pardis.ir.pardisTypeImplicits._

$liftedCode
""")
    }
  }

  def generateTpe[T: universe.TypeTag](implicit al: AutoLifter) {
    val liftedCode = al.autoLift[T]
    val typeName = implicitly[universe.TypeTag[T]].tpe.typeSymbol.name.toString
    val MIRROR_PREFIX = "Mirror"
    val filePostFix = if (typeName.startsWith(MIRROR_PREFIX)) typeName.drop(MIRROR_PREFIX.size) else typeName
    val file = "DeepScala" + filePostFix
    printToFile(new java.io.File(s"$folder/scalalib/$file.scala")) { pw =>
      pw.println(s"""/* Generated by AutoLifter © ${new GregorianCalendar().get(Calendar.YEAR)} */

package ch.epfl.data
package legobase
package deep
package scalalib

import pardis.ir._
import pardis.ir.pardisTypeImplicits._

$liftedCode
""")
    }
  }

  /* from http://stackoverflow.com/questions/4604237/how-to-write-to-a-file-in-scala */
  def printToFile(f: java.io.File)(op: java.io.PrintWriter => Unit) {
    if (reportToFile) {
      val p = new java.io.PrintWriter(f)
      try { op(p) } finally { p.close() }
    } else {
      val p = new java.io.PrintWriter(System.out)
      try { op(p) } finally { p.flush() }
    }
  }
}

