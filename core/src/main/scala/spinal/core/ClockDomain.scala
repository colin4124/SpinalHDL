/*                                                                           *\
**        _____ ____  _____   _____    __                                    **
**       / ___// __ \/  _/ | / /   |  / /   HDL Core                         **
**       \__ \/ /_/ // //  |/ / /| | / /    (c) Dolu, All rights reserved    **
**      ___/ / ____// // /|  / ___ |/ /___                                   **
**     /____/_/   /___/_/ |_/_/  |_/_____/                                   **
**                                                                           **
**      This library is free software; you can redistribute it and/or        **
**    modify it under the terms of the GNU Lesser General Public             **
**    License as published by the Free Software Foundation; either           **
**    version 3.0 of the License, or (at your option) any later version.     **
**                                                                           **
**      This library is distributed in the hope that it will be useful,      **
**    but WITHOUT ANY WARRANTY; without even the implied warranty of         **
**    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU      **
**    Lesser General Public License for more details.                        **
**                                                                           **
**      You should have received a copy of the GNU Lesser General Public     **
**    License along with this library.                                       **
\*                                                                           */
package spinal.core

import spinal.core.ClockDomain.DivisionRate

import scala.collection.mutable.ArrayBuffer

sealed trait EdgeKind
object RISING  extends EdgeKind
object FALLING extends EdgeKind

sealed trait ResetKind
object ASYNC extends ResetKind
object SYNC  extends ResetKind
object BOOT  extends ResetKind

sealed trait Polarity
object HIGH extends Polarity
object LOW  extends Polarity

case class ClockDomainTag(clockDomain: ClockDomain) extends SpinalTag{
  override def toString = s"ClockDomainTag($clockDomain)"
  override def allowMultipleInstance = false
}

sealed trait ClockDomainBoolTag extends SpinalTag{
  override def allowMultipleInstance = true
}
case class ClockTag(clockDomain: ClockDomain)       extends ClockDomainBoolTag
case class ResetTag(clockDomain: ClockDomain)       extends ClockDomainBoolTag
case class ClockEnableTag(clockDomain: ClockDomain) extends ClockDomainBoolTag

trait DummyTrait



// Default configuration of clock domain is :
// Rising edge clock with optional asynchronous reset active high and optional active high clockEnable
case class ClockDomainConfig(clockEdge: EdgeKind = RISING, resetKind: ResetKind = ASYNC, resetActiveLevel: Polarity = HIGH, softResetActiveLevel: Polarity = HIGH, clockEnableActiveLevel: Polarity = HIGH) {
  val useResetPin = resetKind match{
    case `ASYNC` | `SYNC` => true
    case _                => false
  }
}


object ClockDomain {



  /**
    *  Create a local clock domain with `name` as prefix. clock, reset, clockEnable signals should be assigned by your care.
    */
  def internal(name            : String,
               config          : ClockDomainConfig = GlobalData.get.commonClockConfig,
               withReset       : Boolean = true,
               dummyArg        : DummyTrait = null, // dummyArg is here to force the user to use an explicit argument specification
               withSoftReset   : Boolean = false,
               withClockEnable : Boolean = false,
               frequency       : ClockFrequency = UnknownFrequency()): ClockDomain = {

    val clock = Bool()
    clock.setName(if (name != "") name + "_clk" else "clk")

    var reset: Bool = null
    if (withReset && config.resetKind != BOOT) {
      reset = Bool()
      reset.setName((if (name != "") name + "_reset" else "reset") + (if (config.resetActiveLevel == HIGH) "" else "n"))
    }

    var softReset: Bool = null
    if (withSoftReset) {
      softReset = Bool()
      softReset.setName((if (name != "") name + "_soft_reset" else "soft_reset") + (if (config.softResetActiveLevel == HIGH) "" else "n"))
    }

    var clockEnable: Bool = null
    if (withClockEnable) {
      clockEnable = Bool()
      clockEnable.setName((if (name != "") name + "_clkEn" else "clkEn") + (if (config.resetActiveLevel == HIGH) "" else "n"))
    }

    val clockDomain = ClockDomain(clock, reset, dummyArg, softReset, clockEnable, config,  frequency)
    clockDomain
  }


  def defaultConfig = GlobalData.get.commonClockConfig

  /**
    * To use when you want to define a new ClockDomain that thank signals outside the toplevel.
    * (it create input clock, reset, clockenable in the toplevel)
    */
  def external(name            : String,
               config          : ClockDomainConfig = GlobalData.get.commonClockConfig,
               withReset       : Boolean = true,
               dummyArg        : DummyTrait = null, // dummyArg is here to force the user to use an explicit argument specification
               withSoftReset   : Boolean = false,
               withClockEnable : Boolean = false,
               frequency       : ClockFrequency = UnknownFrequency()): ClockDomain = {

    Component.push(null)

    val clockDomain = internal(
      name            = name,
      config          = config,
      withReset       = withReset,
      dummyArg        = dummyArg,
      withSoftReset   = withSoftReset,
      withClockEnable = withClockEnable,
      frequency       = frequency
    )

    Component.pop(null)

    clockDomain
  }

  /** Push a clockdomain on the stack */
  def push(c: ClockDomain): Unit = {
    GlobalData.get.dslClockDomain.push(c)
  }

  /** Pop a clockdomain on the stack */
  def pop(c: ClockDomain): Unit = {
    GlobalData.get.dslClockDomain.pop()
  }

  /** Return the current clock Domain */
  def current: ClockDomain = GlobalData.get.dslClockDomain.head

  def isResetActive       = current.isResetActive
  def isClockEnableActive = current.isClockEnableActive

  def readClockWire       = current.readClockWire
  def readResetWire       = current.readResetWire
  def readClockEnableWire = current.readClockEnableWire

  def getClockDomainDriver(that: Bool): Bool = {
    if (that.existsTag(_.isInstanceOf[ClockDomainBoolTag])) {
      that
    } else {
      that.getSingleDriver match {
        case Some(driver) => getClockDomainDriver(driver)
        case _ => null
      }
    }
  }

  def getClockDomainTag(that: Bool): ClockDomainBoolTag = {
    val driver = getClockDomainDriver(that)
    if (driver == null) {
      null
    } else {
      driver.findTag(_.isInstanceOf[ClockDomainBoolTag]).get.asInstanceOf[ClockDomainBoolTag]
    }
  }


  trait DivisionRate {
    def getValue: BigInt
    def getMax:   BigInt
    def getMin:   BigInt
  }


  case class UnknownDivisionRate() extends DivisionRate {
    def getValue: BigInt = SpinalError("You are trying to get the frequency of a ClockDomain that doesn't know it")
    def getMax:   BigInt = SpinalError("You are trying to get the frequency of a ClockDomain that doesn't know it")
    def getMin:   BigInt = SpinalError("You are trying to get the frequency of a ClockDomain that doesn't know it")
  }


  case class FixedDivisionRate(value: BigInt) extends DivisionRate {
    def getValue: BigInt = value
    def getMax:   BigInt = value
    def getMin:   BigInt = value
  }


  trait ClockFrequency {
    def getValue: HertzNumber
    def getMax:   HertzNumber
    def getMin:   HertzNumber
  }


  case class UnknownFrequency() extends ClockFrequency {
    def getValue: HertzNumber = SpinalError("You are trying to get the frequency of a ClockDomain that doesn't know it")
    def getMax:   HertzNumber = SpinalError("You are trying to get the frequency of a ClockDomain that doesn't know it")
    def getMin:   HertzNumber = SpinalError("You are trying to get the frequency of a ClockDomain that doesn't know it")
  }


  case class FixedFrequency(value: HertzNumber) extends ClockFrequency {
    def getValue: HertzNumber = value
    def getMax:   HertzNumber = value
    def getMin:   HertzNumber = value
  }


}



case class ClockSyncTag(a : Bool, b : Bool) extends SpinalTag{
  override def canSymplifyHost: Boolean = false
}
case class ClockDrivedTag(driver : Bool) extends SpinalTag{
  override def canSymplifyHost: Boolean = false
}
case class ClockDriverTag(drived : Bool) extends SpinalTag{
  override def canSymplifyHost: Boolean = false
}

object Clock{
  def syncDrive(source : Bool, sink : Bool): Unit ={
    source.addTag(ClockDriverTag(sink))
    sink.addTag(ClockDrivedTag(source))
  }
}

/**
  * clock and reset signals can be combined to create a clock domain.
  * Clock domains could be applied to some area of the design and then all synchronous elements instantiated into this
  * area will then implicitly use this clock domain.
  * Clock domain application work like a stack, which mean, if you are in a given clock domain, you can still apply another clock domain locally
  *
  * @see  [[http://spinalhdl.github.io/SpinalDoc/spinal/core/clock_domain ClockDomain Documentation]]
  */
case class ClockDomain(clock       : Bool,
                       reset       : Bool = null,
                       dummyArg    : DummyTrait = null, // dummyArg is here to force the user to use an explicit argument specification
                       softReset   : Bool = null,
                       clockEnable : Bool = null,
                       config      : ClockDomainConfig = GlobalData.get.commonClockConfig,
                       frequency   : ClockDomain.ClockFrequency = UnknownFrequency(),
                       clockEnableDivisionRate : ClockDomain.DivisionRate = ClockDomain.UnknownDivisionRate()) {

  assert(!(reset != null && config.resetKind == BOOT), "A reset pin was given to a clock domain where the config.resetKind is 'BOOT'")

  val instanceCounter = GlobalData.get.getInstanceCounter

  clock.addTag(ClockTag(this))

  if (reset != null) reset.addTag(ResetTag(this))
  if (clockEnable != null) clockEnable.addTag(ClockEnableTag(this))

  def hasClockEnableSignal = clockEnable != null
  def hasResetSignal       = reset != null
  def hasSoftResetSignal   = softReset != null

  def push(): Unit = ClockDomain.push(this)
  def pop(): Unit  = ClockDomain.pop(this)

  def isResetActive = {
    if(config.useResetPin && reset != null)
      if (config.resetActiveLevel == HIGH) readResetWire else !readResetWire
    else
      False
  }

  def isSoftResetActive = {
    if(softReset != null)
      if (config.softResetActiveLevel == HIGH) readSoftResetWire else ! readSoftResetWire
    else
      False
  }

  def isClockEnableActive = {
    if(clockEnable != null)
      if (config.clockEnableActiveLevel == HIGH) readClockEnableWire else !readClockEnableWire
    else
      True
  }

  def readClockWire       = if (null == clock) Bool(config.clockEdge == FALLING)                 else Data.doPull(clock, Component.current, useCache = true, propagateName = true)
  def readResetWire       = if (null == reset) Bool(config.resetActiveLevel == LOW)              else Data.doPull(reset, Component.current, useCache = true, propagateName = true)
  def readSoftResetWire   = if (null == softReset) Bool(config.softResetActiveLevel == LOW)      else Data.doPull(softReset, Component.current, useCache = true, propagateName = true)
  def readClockEnableWire = if (null == clockEnable) Bool(config.clockEnableActiveLevel == HIGH) else Data.doPull(clockEnable, Component.current, useCache = true, propagateName = true)


  def setSyncWith(that: ClockDomain) : this.type = {
    val tag = new ClockSyncTag(this.clock, that.clock)
    this.clock.addTag(tag)
    that.clock.addTag(tag)
    this
  }
  def setSynchronousWith(that: ClockDomain) : this.type = setSyncWith(that)
  @deprecated("misspelled method will be removed", "SpinalHDL 1.2.3")
  def setSyncronousWith(that: ClockDomain) = setSyncWith(that)

  def apply[T](block: => T): T = {
    push()
    val ret: T = block
    pop()
    ret
  }

  def on [T](block : => T) : T = apply(block)

  /** Slow down the current clock to factor time */
  def newClockDomainSlowedBy(factor: BigInt): ClockDomain = factor match {
    case x if x == 1 => this.copy()
    case x if x > 1  => this{
      val counter = Reg(UInt(log2Up(factor) bits)) init (0)
      val tick = counter === factor - 1
      counter := counter + 1
      when(tick) {
        counter := 0
      }

      val currentDivisionRate = if(ClockDomain.current.clockEnable == null) ClockDomain.FixedDivisionRate(1) else  ClockDomain.current.clockEnableDivisionRate
      val divisionRate = new DivisionRate {
        override def getValue: BigInt = currentDivisionRate.getValue*factor
        override def getMax: BigInt = currentDivisionRate.getMax*factor
        override def getMin: BigInt =  currentDivisionRate.getMin*factor
      }
      this.copy(clockEnable = RegNext(tick) init(False), clockEnableDivisionRate = divisionRate, config = ClockDomain.current.config.copy(clockEnableActiveLevel = HIGH))
    }
  }

  def newSlowedClockDomain(freq: HertzNumber): ClockDomain = {
    val currentFreq = ClockDomain.current.frequency.getValue.toBigDecimal
    freq match {
      case x if x.toBigDecimal > currentFreq => SpinalError("To high frequancy")
      case x                                 => newClockDomainSlowedBy((currentFreq/freq.toBigDecimal).toBigInt())
    }
  }

  @deprecated("Use copy instead of clone", "1.3.0")
  def clone(config      : ClockDomainConfig = config,
            clock       : Bool = clock,
            reset       : Bool = reset,
            dummyArg    : DummyTrait = null,
            softReset   : Bool = softReset,
            clockEnable : Bool = clockEnable): ClockDomain = {
    this.copy(clock, reset, dummyArg, softReset, clockEnable, config, frequency)
  }

  override def toString = clock.getName("???")

  def withRevertedClockEdge() = {
    copy(config = config.copy(clockEdge = if(config.clockEdge == RISING) FALLING else RISING))
  }

  def samplingRate : IClockDomainFrequency = {
    if(clockEnable == null) return frequency
    try{
      val f = new IClockDomainFrequency{
        override def getValue: HertzNumber = frequency.getValue/BigDecimal(clockEnableDivisionRate.getValue)
        override def getMax: HertzNumber = frequency.getMax/BigDecimal(clockEnableDivisionRate.getMin)
        override def getMin: HertzNumber = frequency.getMin/BigDecimal(clockEnableDivisionRate.getMax)
      }
      //Test it
      f.getValue
      f.getMax
      f.getMin

      return f
    } catch {
      case _ : Throwable => return UnknownFrequency()
    }
  }
}





