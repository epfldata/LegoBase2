package ch.epfl.data.dblab.frontend.optimizer

import ch.epfl.data.dblab.frontend.parser.CalcAST._
import ch.epfl.data.dblab.frontend.optimizer.CalcUtils._
import ch.epfl.data.dblab.frontend.parser.CalcAST
import ch.epfl.data.dblab.frontend.parser.SQLAST.IntLiteral
import ch.epfl.data.dblab.schema._
import ch.epfl.data.sc.pardis.types._

import ch.epfl.data.dblab.frontend.optimizer.CalcOptimizer._
import scala.collection.mutable.ArrayBuffer
import scala.tools.nsc.interactive.Lexer.IntLit

/**
 * @author Parand Alizadeh
 */

object CalcCompiler {

  var rawCurSuffix = 0

  def extractRelations(expr: CalcExpr, dbSchema: Schema, prefix: String, schemaT: SchemaT): (List[Ds], CalcExpr) = {

    def merge(op: (List[CalcExpr] => CalcExpr))(schemaT: SchemaT, terms: List[(List[Ds], CalcExpr)]): (List[Ds], CalcExpr) = {
      val (dses, exprs) = terms.unzip
      val res = dses.foldLeft(List.empty[Ds])((acc, cur) => acc ::: cur)
      (res, op(exprs))
    }

    def rcr(schemaT: SchemaT, exp: CalcExpr) = extractRelations(exp, dbSchema, prefix, schemaT)

    def rcrWrap(wrap: (CalcExpr => CalcExpr), schemaT: SchemaT, expr: CalcExpr): (List[Ds], CalcExpr) = {
      val (dses, rete) = rcr(schemaT, expr)
      (dses, wrap(rete))
    }

    def isStream(relName: String): Boolean = {
      val t = rel(dbSchema, relName)
      tableHasStream(t)
    }

    def nextPrefix(reln: String): String = {
      rawCurSuffix = rawCurSuffix + 1
      s"${prefix}_raw_reln_${rawCurSuffix}"
    }

    def leaf(schemaT: SchemaT, expr: CalcExpr): (List[Ds], CalcExpr) = {
      expr match {
        case Rel(_, rname, rv, _) if isStream(rname) => {
          val relSchema = rv
          materializeAsExternal(expr, dbSchema, nextPrefix(rname), None, schemaT.scope)
        }

        case DomainDelta(exp)     => rcrWrap((x => DomainDelta(x)), schemaT, exp)
        case Exists(subexp)       => rcrWrap((x => Exists(x)), schemaT, subexp)
        case AggSum(gbv, subterm) => rcrWrap((x => AggSum(gbv, x)), SchemaT(schemaT.scope, gbv), subterm)
        case Lift(v, subexp)      => rcrWrap((x => Lift(v, x)), SchemaT(schemaT.scope, List()), subexp)
        case _                    => (List(), expr)
      }
    }

    def neg(schemaT: SchemaT, dses: (List[Ds], CalcExpr)): (List[Ds], CalcExpr) = (dses._1, CalcNeg(dses._2))

    foldCalculus(expr, schemaT, merge(sumGeneral), merge(prodGeneral), neg, leaf)
  }
  def materializeAsExternal(expr: CalcExpr, dbSchema: Schema, prefix: String, event: Option[EventT], schema: List[VarT]): (List[Ds], CalcExpr) = {

    var history = List.empty[Ds]
    if (relsOfExpr(expr).length == 0 && deltaRelsOfExpression(expr).length == 0)
      (List(), expr)
    else {
      val aggExpr = AggSum(schema, expr)
      val tableVars = List()

      val (foundDs, mappingIfFound) = (Ds(CalcOne, CalcOne), None)
      mappingIfFound match {
        case None => {
          val exprIvars = schemaOfExpression(expr)._1
          val (todoIvc, ivcExpr) = (List(), None)

          val newDS = Ds(External(prefix, exprIvars, schema, typeOfExpression(aggExpr), ivcExpr), aggExpr)
          history = newDS :: history

          (newDS :: todoIvc, newDS.dsname)
        }
      }
    }
  }

  //TODO not sure
  def rel(db: Schema, reln: String): Table = {
    val r = db.tables.find(x => x.name.equals(reln))
    r match {
      case Some(x) => x
      case _       => null
    }
  }
  def compileMap(todot: TodoT, computedelta: Boolean, dbschema: Schema): (List[TodoT], CompiledDs) = {
    val ext = todot.ds.dsname match {
      case External(name, inps, outs, tp, Some(s)) => External(name, inps, outs, tp, Some(s))
      case External(name, inps, outs, tp, None)    => External(name, inps, outs, tp, None)
      case _                                       => throw new Exception
    }

    // println(ext.tp)
    val todotype = ext.tp match {
      case IntType     => ext.tp
      case FloatType   => ext.tp
      case DoubleType  => ext.tp
      case BooleanType => IntType
      case _           => throw new Exception

    }
    //TODO optimiziation removed
    val optimizedDefn = todot.ds.dsdef

    val rels = relsOfExpr(ext).map(x => rel(dbschema, x))
    val (tablerels, streamrels) = rels.partition(tableHasStream)

    val events = List(((x: Table) => DeleteEvent(x), "_m"), ((x: Table) => InsertEvent(x), "_p"))

    var triggers = List.empty[Trigger]

    var triggerTodos = List.empty[TodoT]

    for (rel <- streamrels) {
      for (evt <- events) {
        val mapPrefix = ext.name + evt._2 + rel.name
        //TODO not sure
        val prefixedRelv = rel.attributes.map(x => Attribute(mapPrefix + x.name, x.dataType, x.constraints, x.distinctValuesCount, x.nullValuesCount))
        val deltaEve = evt._1(Table(rel.name, rel.attributes, (new ArrayBuffer[Constraint](1)) += (StreamingTable), ""))

        //TODO heuristic ****

        if (computedelta) {
          val deltaUnop = deltaOfExpr(deltaEve, optimizedDefn)
          //TODO optimize
          val deltaUnextracted = deltaUnop
          val (deltaRenamings, deltaExpr) = extractRenamings(prefixedRelv.map(toVarT), ext.outs, deltaUnextracted)
          //TODO scope
          val (newTodos, materlizedData) = materializeAsExternal(deltaExpr, dbschema, mapPrefix, Some(deltaEve), List())
          //TODO skip ivc is true
          val mergedTodos = newTodos
          for (tod <- mergedTodos) {
            if (deltaRelsOfExpression(tod.dsdef).length != 0)
              triggers = triggers :+ Trigger(deltaEve, StmtT(tod.dsname, ReplaceStmt, tod.dsdef))

          }
          triggerTodos = mergedTodos.map(x => TodoT(todot.depth + 1, x, true)) ::: triggerTodos

          triggers = Trigger(deltaEve, StmtT(External(ext.name, ext.inps.map(x => applyIfPresent(deltaRenamings)(x)), ext.outs.map(x => applyIfPresent(deltaRenamings)(x)), ext.tp, None), UpdateStmt, materlizedData)) :: triggers

        } else {
          val (newTodos, materializeExpr) = materializeAsExternal(optimizedDefn, dbschema, mapPrefix, Some(deltaEve), List())
          for (tod <- newTodos) {
            if (deltaRelsOfExpression(tod.dsdef).length != 0)
              triggers = Trigger(deltaEve, StmtT(tod.dsname, ReplaceStmt, tod.dsdef)) :: triggers

          }

          triggerTodos = newTodos.map(x => TodoT(todot.depth + 1, x, true)) ::: triggerTodos
          triggers = Trigger(deltaEve, StmtT(todot.ds.dsname, ReplaceStmt, materializeExpr)) :: triggers

        }

      }
    }

    //TODO IVC
    val (initTodos, initExpr) = {
      if (ext.inps.length != 0) {
        //val (ini_todo, init_exp) = materializeAsExternal(optimizedDefn, )
        (List.empty[Ds], Some(CalcOne))
      } else {
        (List.empty[Ds], None)
      }
    }

    (triggerTodos ::: initTodos.map(x => TodoT(todot.depth + 1, x, true)), CompiledDs(Ds(External(ext.name, ext.inps, ext.outs, ext.tp, initExpr), optimizedDefn), triggers))

  }

  def compileTables(rel: Rel): CompiledDs = {
    val mapName = External("_" + rel.name, List(), rel.vars, IntType, None)
    val discription = Ds(mapName, Rel("Rel", rel.name, rel.vars, ""))
    val mone = CalcNeg(CalcOne)

    var l = List.empty[(EventT, CalcExpr)]
    l = List((InsertEvent(Table(rel.name, rel.vars.map(toAttribute), ArrayBuffer(), "")), CalcOne), (DeleteEvent(Table(rel.name, rel.vars.map(toAttribute), ArrayBuffer(), "")), mone))
    val triggers = l.map(x => {
      Trigger(x._1, StmtT(mapName, UpdateStmt, x._2))
    })

    CompiledDs(discription, triggers)
  }

  //checked
  def compileTlqs(calcQueries: List[CalcQuery], dbSchema: Schema): (List[TodoT], List[CalcQuery]) = {
    val (todolists, toplevelqueries) = calcQueries.map(x => {

      val qschema = schemaOfExpression(x.expr)
      val qtype = typeOfExpression(x.expr)

      val dsname = External(x.name, qschema._1, qschema._2, qtype, None)
      (TodoT(1, Ds(dsname, x.expr), false), CalcQuery(x.name, dsname))
    }).unzip

    (todolists, toplevelqueries)

  }

  def compile(max_depth: Option[Int], calcQueris: List[CalcQuery], dbSchema: Schema): (Plan, List[CalcQuery]) = {
    var (todolist, tlqlist) = compileTlqs(calcQueris, dbSchema)
    //check huristic options
    var plan = List.empty[CompiledDs]
    while (todolist.length > 0) {
      val nextds = todolist.head
      todolist = todolist.tail
      val depth = nextds.depth
      val todoo = nextds.ds
      val skip = nextds.b

      val computedelta = max_depth match {
        case None    => true
        case Some(d) => (d > 0 || depth > 1)
      }

      val (newtodos, compiledds) = compileMap(nextds, computedelta, dbSchema)

      todolist = newtodos ::: todolist
      plan = plan :+ compiledds
    }
    (Plan(plan), tlqlist)
  }

}
