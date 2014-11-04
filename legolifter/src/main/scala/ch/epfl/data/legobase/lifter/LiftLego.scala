package ch.epfl.data
package legobase
package lifter

import scala.reflect.runtime.universe

import queryengine.volcano._
import pardis.annotations._
import purgatory._
import purgatory.config._
import purgatory.generator._
import purgatory.lifter._
import java.util.{ Calendar, GregorianCalendar }

object LiftLego extends PardisLifter {

  def main(args: Array[String]) {
    implicit val al = new AutoLifter(universe)
    generateLegoBase
    generateCLibs
  }

  val folder = "legocompiler/src/main/scala/ch/epfl/data/legobase/deep"

  def generateLegoBase(implicit al: AutoLifter) {
    val custom = Custom(requiredOps = List("OptimalStringOps"))
    val liftedCodes = List(
      al.autoLift[queryengine.GroupByClass](custom),
      al.autoLift[queryengine.Q3GRPRecord](custom),
      al.autoLift[queryengine.Q7GRPRecord](custom),
      al.autoLift[queryengine.Q9GRPRecord](custom),
      al.autoLift[queryengine.Q10GRPRecord](custom),
      al.autoLift[queryengine.Q16GRPRecord1](custom),
      al.autoLift[queryengine.Q16GRPRecord2](custom),
      al.autoLift[queryengine.Q18GRPRecord](custom),
      al.autoLift[queryengine.Q20GRPRecord](custom),
      al.autoLift[queryengine.AGGRecord[Any]](custom),
      al.autoLift[queryengine.GenericEngine](custom),
      al.autoLift[queryengine.TPCHRelations.LINEITEMRecord](custom),
      al.autoLift[queryengine.TPCHRelations.SUPPLIERRecord](custom),
      al.autoLift[queryengine.TPCHRelations.PARTSUPPRecord](custom),
      al.autoLift[queryengine.TPCHRelations.REGIONRecord](custom),
      al.autoLift[queryengine.TPCHRelations.NATIONRecord](custom),
      al.autoLift[queryengine.TPCHRelations.PARTRecord](custom),
      al.autoLift[queryengine.TPCHRelations.CUSTOMERRecord](custom),
      al.autoLift[queryengine.TPCHRelations.ORDERSRecord](custom),
      // al.autoLift[storagemanager.Loader](custom),
      al.autoLift[NextContainer[_]](custom),
      al.autoLift[NextKeyContainer[_, _]](custom),
      al.autoLift[storagemanager.K2DBScanner](custom.copy(excludedFields = List(CMethod("br"), CMethod("sdf")))),
      al.autoLift[queryengine.WindowRecord[Any, Any]](custom))
    val liftedCode = liftedCodes.mkString("\n")
    val file = "DeepLegoBase"
    printToFile(new java.io.File(s"$folder/$file.scala")) { pw =>
      pw.println(s"""/* Generated by AutoLifter ${new GregorianCalendar().get(Calendar.YEAR)} */

package ch.epfl.data
package legobase
package deep

import pardis.ir._
import pardis.types.PardisTypeImplicits._
import pardis.deep.scalalib._
import pardis.deep.scalalib.collection._
import pardis.deep.scalalib.io._

$liftedCode
trait DeepDSL extends push.OperatorsComponent 
  with AGGRecordComponent 
  with WindowRecordComponent 
  with CharComponent 
  with DoubleComponent 
  with IntComponent 
  with LongComponent 
  with BooleanComponent 
  with DoublePartialEvaluation 
  with IntPartialEvaluation 
  with LongPartialEvaluation 
  with BooleanPartialEvaluation 
  with ArrayComponent
  with PrintStreamComponent 
  with GroupByClassComponent
  with Q3GRPRecordComponent
  with Q7GRPRecordComponent
  with Q9GRPRecordComponent
  with Q10GRPRecordComponent
  with Q16GRPRecord1Component
  with Q16GRPRecord2Component
  with Q18GRPRecordComponent
  with Q20GRPRecordComponent
  with GenericEngineComponent
  with LINEITEMRecordComponent
  with SUPPLIERRecordComponent
  with PARTSUPPRecordComponent
  with REGIONRecordComponent
  with NATIONRecordComponent
  with PARTRecordComponent
  with CUSTOMERRecordComponent
  with ORDERSRecordComponent
  with OptimalStringComponent
  with LoaderComponent
  with K2DBScannerComponent 
  with IntegerComponent 
  with NextContainerComponent
  with NextKeyContainerComponent
  with HashMapComponent 
  with SetComponent 
  with TreeSetComponent 
  with ArrayBufferComponent 
  with ManualLiftedLegoBase 
  with QueryComponent
""")
    }
  }

  def generateCLibs(implicit al: AutoLifter) {
    val liftedCodes = List(
      al.autoLift[ch.epfl.data.pardis.shallow.c.CLPointerType[_]](Custom(Some("CLibs"))),
      al.autoLift[ch.epfl.data.pardis.shallow.c.CLang](Custom(Some("CLibs"))),
      al.autoLift[ch.epfl.data.pardis.shallow.c.CStdLib](Custom(Some("CLibs"))),
      al.autoLift[ch.epfl.data.pardis.shallow.c.CStdIO](Custom(Some("CLibs"))),
      al.autoLift[ch.epfl.data.pardis.shallow.c.CFileType](Custom(Some("CLibs"))),
      al.autoLift[ch.epfl.data.pardis.shallow.c.CTimeValType](Custom(Some("CLibs"))),
      al.autoLift[ch.epfl.data.pardis.shallow.c.CSysTime](Custom(Some("CLibs"))),
      al.autoLift[ch.epfl.data.pardis.shallow.c.CString](Custom(Some("CLibs"))),
      al.autoLift[ch.epfl.data.pardis.shallow.c.LGArrayType](Custom(Some("CLibs"))),
      al.autoLift[ch.epfl.data.pardis.shallow.c.LGArrayHeader](Custom(Some("CLibs"))),
      al.autoLift[ch.epfl.data.pardis.shallow.c.LGListType[_]](Custom(Some("CLibs"))),
      al.autoLift[ch.epfl.data.pardis.shallow.c.LGListHeader](Custom(Some("CLibs"))),
      al.autoLift[ch.epfl.data.pardis.shallow.c.LGTreeType](Custom(Some("CLibs"))),
      al.autoLift[ch.epfl.data.pardis.shallow.c.LGTreeHeader](Custom(Some("CLibs"))),
      al.autoLift[ch.epfl.data.pardis.shallow.c.LGHashTableType[_, _]](Custom(Some("CLibs"))),
      al.autoLift[ch.epfl.data.pardis.shallow.c.LGHashTableHeader](Custom(Some("CLibs"))))
    val liftedCode = liftedCodes.mkString("\n")
    val file = "GLib"
    printToFile(new java.io.File(s"$folder/scalalib/$file.scala")) { pw =>
      pw.println(s"""/* Generated by AutoLifter © ${new GregorianCalendar().get(Calendar.YEAR)} */

package ch.epfl.data
package legobase
package deep

import pardis.ir._
import pardis.types.PardisTypeImplicits._
import pardis.shallow.c.CLangTypes._
import pardis.shallow.c.GLibTypes._

trait CLibs extends LPointerComponent
  with CLangComponent
  with CStdLibComponent
  with CFileComponent
  with CStdIOComponent
  with CStringComponent
  with CTimeValComponent
  with CSysTimeComponent
  with LGTreeComponent
  with LGTreeHeaderComponent
  with LGListComponent
  with LGListHeaderComponent
  with LGArrayComponent
  with LGArrayHeaderComponent
  with LGHashTableComponent
  with LGHashTableHeaderComponent

$liftedCode
""")
    }
  }
}

