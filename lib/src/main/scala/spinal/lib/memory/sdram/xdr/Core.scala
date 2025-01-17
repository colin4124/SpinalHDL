package spinal.lib.memory.sdram.xdr

import spinal.core._
import spinal.lib._

case class CoreParameterAggregate(cp : CoreParameter, pl : PhyParameter, cpp : Seq[CorePortParameter]){
  def backendContextWidth = cpp.map(_.contextWidth).max
  def portCount = cpp.size
  def generation = pl.sdram.generation
}

case class Core(cpa : CoreParameterAggregate) extends Component {
  import cpa._

  val io = new Bundle {
    val config = in(CoreConfig(cpa))
    val soft = slave(SoftBus(cpa))
    val ports = Vec(cpp.map(cpp => slave(CorePort(cpp, cpa))))
    val phy = master(SdramXdrPhyCtrl(pl))
    val refresh = out Bool()
  }

  val refresher = Refresher(cpa)
  refresher.io.config <> io.config
  io.refresh := refresher.io.refresh.valid

  val tasker = Tasker(cpa)
  tasker.io.config <> io.config
  tasker.io.inputs <> Vec(io.ports.map(_.cmd))
  tasker.io.refresh <> refresher.io.refresh

  val backend = Backend(cpa)
  backend.io.config <> io.config
  backend.io.input := tasker.io.output.stage()
  backend.io.phy <> io.phy
  backend.io.soft <> io.soft
  (backend.io.outputs, io.ports).zipped.foreach(_.toStream <> _.rsp)
}

