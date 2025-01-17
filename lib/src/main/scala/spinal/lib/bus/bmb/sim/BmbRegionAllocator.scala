package spinal.lib.bus.bmb.sim

import spinal.lib.bus.bmb.{Bmb, BmbParameter}
import spinal.lib.bus.misc.SizeMapping

import scala.collection.mutable
import scala.util.Random

case class BmbRegionAllocator(alignmentMinWidth : Int = 0){
  val allocations = mutable.HashSet[SizeMapping]()

  def align(v : Int) = v & ~((1 << alignmentMinWidth) -1)
  def free(region : SizeMapping) = allocations.remove(region)
  def allocate(addressGen : => Int, sizeMax : Int, p : BmbParameter, sizeMin : Int = 1) : SizeMapping = {
    val sizeMinAligned = Math.max(sizeMin, 1 << alignmentMinWidth)
    while(true){
      var address = align(addressGen)
      val boundaryMax = Bmb.boundarySize - (address & (Bmb.boundarySize-1))
      var size = Math.max(sizeMinAligned, align(Math.min(boundaryMax, Random.nextInt(sizeMax) + 1)))
      if(!p.alignment.allowByte) {
        address &= ~p.wordMask
        if(size > p.byteCount){
          size = (size + p.byteCount - 1) & ~p.wordMask
        }
      }
      val region = SizeMapping(address, size)
      if(allocations.forall(r => r.base > region.end || r.end < region.base) && size <= boundaryMax) {
        allocations += region
        return region
      }
    }
    return null
  }
}
