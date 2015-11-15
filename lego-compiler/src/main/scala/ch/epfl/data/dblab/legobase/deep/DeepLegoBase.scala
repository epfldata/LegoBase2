package ch.epfl.data
package dblab.legobase
package deep

import sc.pardis.ir._
import sc.pardis.types.PardisTypeImplicits._
import sc.pardis.deep._
import sc.pardis.deep.scalalib._
import sc.pardis.deep.scalalib.collection._
import sc.pardis.deep.scalalib.io._
import dblab.legobase.deep.queryengine._
import dblab.legobase.deep.storagemanager._
import dblab.legobase.deep.tpch._

/** A polymorphic embedding cake which chains all components needed for TPCH queries */
trait DeepDSL extends queryengine.push.OperatorsComponent
  with AGGRecordComponent
  with WindowRecordComponent
  // with CharComponent
  // with DoubleComponent
  // with IntComponent
  // with LongComponent
  // with BooleanComponent
  // with DoublePartialEvaluation
  // with IntPartialEvaluation
  // with LongPartialEvaluation
  // with BooleanPartialEvaluation
  // with ArrayComponent
  // with SeqComponent
  // with PrintStreamComponent
  // with Q1GRPRecordComponent
  // with Q3GRPRecordComponent
  // with Q7GRPRecordComponent
  // with Q9GRPRecordComponent
  // with Q10GRPRecordComponent
  // with Q13IntRecordComponent
  // with Q16GRPRecord1Component
  // with Q16GRPRecord2Component
  // with Q18GRPRecordComponent
  // with Q20GRPRecordComponent
  with GenericEngineComponent
  // with LINEITEMRecordComponent
  // with SUPPLIERRecordComponent
  // with PARTSUPPRecordComponent
  // with REGIONRecordComponent
  // with NATIONRecordComponent
  // with PARTRecordComponent
  // with CUSTOMERRecordComponent
  // with ORDERSRecordComponent
  // with OptimalStringComponent
  with LoaderComponent
  // with K2DBScannerComponent
  // with IntegerComponent
  // with HashMapComponent
  // with SetComponent
  // with TreeSetComponent
  // with ArrayBufferComponent
  with ManualLiftedLegoBase
  with QueryComponent
  // with Tuple2Component
  // with Tuple3Component
  // with Tuple4Component
  // with Tuple9Component
  // with MultiMapComponent
  // with OptionComponent
  // with StringComponent
  with SynthesizedQueriesComponent
  with TPCHLoaderComponent
  with monad.GroupedQueryComponent
  with monad.QueryComponent
  with monad.JoinableQueryComponent
  with ScalaCoreDSLPartialEvaluation
  with TPCHRecords
