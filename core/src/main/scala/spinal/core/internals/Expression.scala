package spinal.core.internals

import spinal.core._

import scala.collection.mutable.ArrayBuffer


trait Expression extends BaseNode with ExpressionContainer{
  def opName : String
  def simplifyNode: Expression = this
  def getTypeObject : Any
  private[core] def foreachDrivingExpression(outHi : Int, outLo : Int)(f : (Expression, Int,Int) => Unit) : Unit = foreachDrivingExpression{
    case input : Expression with WidthProvider => f(input, input.getWidth-1,0)
    case input => f(input, 0,0)
  }

  override def toString = opName
}


trait ExpressionContainer{
  def normalizeInputs: Unit = {}
  def remapExpressions(func : (Expression) => Expression) : Unit
  def remapDrivingExpressions(func : (Expression) => Expression) : Unit = remapExpressions(func)
  def foreachExpression(func : (Expression) => Unit) : Unit
  def foreachDrivingExpression(func : (Expression) => Unit) : Unit = foreachExpression(func)
  //  def foreachDrivingExpressionWithDelay(func : (Expression, Int) => Unit) : Unit = foreachExpression(func(_,0))

  def walkExpression(func : (Expression) => Unit) : Unit = {
    foreachExpression(e => {
      func(e)
      e.walkExpression(func)
    })
  }
  def walkDrivingExpressions(func : (Expression) => Unit) : Unit = {
    foreachDrivingExpression(e => {
      func(e)
      e.walkDrivingExpressions(func)
    })
  }
  def walkRemapExpressions(func : (Expression) => Expression) : Unit = {
    remapExpressions(func)
    foreachExpression(e => {
      e.walkRemapExpressions(func)
    })
  }
  def walkRemapDrivingExpressions(func : (Expression) => Expression) : Unit = {
    remapDrivingExpressions(func)
    foreachDrivingExpression(e => {
      e.walkRemapDrivingExpressions(func)
    })
  }
}




abstract class AnalogDriver extends Expression{
  type T <: Expression
  var data  : T = null.asInstanceOf[T]
  var enable : Expression = null

  def foreachExpression(func : (Expression) => Unit) : Unit = {
    func(data)
    func(enable)
  }

  override def remapExpressions(func: (Expression) => Expression): Unit = {
    data = func(data).asInstanceOf[T]
    enable = func(enable)
  }

  override def toStringMultiLine() = {
    s"""$this
       |- data  operand : $data
       |- enable operand : $enable
       |""".stripMargin
  }
}


abstract class Resize extends Expression with WidthProvider{
  var size : Int = -1
  var input : Expression with WidthProvider = null

  override def getWidth(): Int = size


  override def simplifyNode: Expression = {
    if(input.getWidth == 0){
      getLiteralFactory(0,size)
    } else {
      this
    }
  }
  def getLiteralFactory : (BigInt, Int) => Expression
  override def foreachExpression(func: (Expression) => Unit): Unit = func(input)
  override def remapExpressions(func: (Expression) => Expression): Unit = input = func(input).asInstanceOf[Expression with WidthProvider]
}

class ResizeBits extends Resize{
  override def getTypeObject = TypeBits
  override def opName: String = s"resize(Bits,$size bits)"
  override def getLiteralFactory: (BigInt, Int) => Expression = BitsLiteral.apply
  //  override def simplifyNode: Unit = SymplifyNode.resizeImpl2(B.apply,this)
}
class ResizeUInt extends Resize{
  override def getTypeObject = TypeUInt
  override def opName: String = s"resize(UInt,$size bits)"
  override def getLiteralFactory: (BigInt, Int) => Expression = UIntLiteral.apply
}
class ResizeSInt extends Resize{
  override def getTypeObject = TypeSInt
  override def opName: String = s"resize(SInt,$size bits)"
  override def getLiteralFactory: (BigInt, Int) => Expression = SIntLiteral.apply
}




abstract class Operator extends Modifier{
}

abstract class UnaryOperator extends Operator{
  type T <: Expression
  var source  : T = null.asInstanceOf[T]

  def foreachExpression(func : (Expression) => Unit) : Unit = {
    func(source)
  }

  override def remapExpressions(func: (Expression) => Expression): Unit = {
    source = func(source).asInstanceOf[T]
  }
}

abstract class UnaryOperatorWidthableInputs extends UnaryOperator with Widthable{
  override type T = Expression with WidthProvider
}


abstract class ConstantOperator extends Operator{
  type T <: Expression
  var source  : T = null.asInstanceOf[T]

  def foreachExpression(func : (Expression) => Unit) : Unit = {
    func(source)
  }

  override def remapExpressions(func: (Expression) => Expression): Unit = {
    source = func(source).asInstanceOf[T]
  }
}



abstract class ConstantOperatorWidthableInputs extends ConstantOperator{
  override type T = Expression with WidthProvider
}



abstract class BinaryOperator extends Operator{
  type T <: Expression
  var left,right  : T = null.asInstanceOf[T]

  def foreachExpression(func : (Expression) => Unit) : Unit = {
    func(left)
    func(right)
  }

  override def remapExpressions(func: (Expression) => Expression): Unit = {
    left = func(left).asInstanceOf[T]
    right = func(right).asInstanceOf[T]
  }

  //  override def toString(): String = {
  //    def inStr(that : T) = (if (that == null) "null" else that.toString())
  //    s"(${inStr(left)} $opName ${inStr(right)})"
  //  }
  override def toStringMultiLine() = {
    s"""$this
       |- Left  operand : $left
       |- Right operand : $right
       |""".stripMargin
  }
}


abstract class BinaryOperatorWidthableInputs extends BinaryOperator{
  override type T = Expression with WidthProvider

  override def toString() = super.toString()
}


object InferWidth{
  def canBeResized(that : Expression) = that match {
    case that : SpinalTagReady => that.hasTag(tagAutoResize)
    case _ => false
  }
  def notResizableElseMax(op : BinaryOperatorWidthableInputs) : Int = {
    val leftR = canBeResized(op.left)
    val rightR = canBeResized(op.right)
    if(leftR != rightR){
      if(leftR) op.right.getWidth else op.left.getWidth
    } else {
      Math.max(op.left.getWidth, op.right.getWidth)
    }
  }

  def notResizableElseMax(op : MultiplexedWidthable) : Int = {
    val leftR = canBeResized(op.whenTrue)
    val rightR = canBeResized(op.whenFalse)
    if(leftR != rightR){
      if(leftR) op.whenFalse.getWidth else op.whenTrue.getWidth
    } else {
      Math.max(op.whenTrue.getWidth, op.whenFalse.getWidth)
    }
  }
}



object Operator{
  object Bool{
    class And extends BinaryOperator{
      override def getTypeObject = TypeBool
      override def opName: String = "Bool && Bool"
    }

    class Or extends BinaryOperator{
      override def getTypeObject = TypeBool
      override def opName: String = "Bool || Bool"
    }

    class Xor extends BinaryOperator{
      override def getTypeObject = TypeBool
      override def opName: String = "Bool ^ Bool"
    }

    class Not extends UnaryOperator{
      override def getTypeObject = TypeBool
      override def opName: String = "! Bool"
    }

    class Equal extends BinaryOperator{
      override def getTypeObject = TypeBool
      override def opName: String = "Bool === Bool"
    }

    class NotEqual extends BinaryOperator{
      override def getTypeObject = TypeBool
      override def opName: String = "Bool =/= Bool"
    }
  }
  //
  object BitVector{
    abstract class And extends BinaryOperatorWidthableInputs with Widthable{
      def resizeFactory : Resize
      override def calcWidth(): Int = InferWidth.notResizableElseMax(this)
      override def normalizeInputs: Unit = {
        val targetWidth = getWidth
        left = InputNormalize.resizedOrUnfixedLit(left, targetWidth, resizeFactory, this, this)
        right = InputNormalize.resizedOrUnfixedLit(right, targetWidth, resizeFactory, this, this)
      }
    }

    abstract class Or extends BinaryOperatorWidthableInputs with Widthable{
      def resizeFactory : Resize
      override def calcWidth(): Int = InferWidth.notResizableElseMax(this)
      override def normalizeInputs: Unit = {
        val targetWidth = getWidth
        left = InputNormalize.resizedOrUnfixedLit(left, targetWidth, resizeFactory, this, this)
        right = InputNormalize.resizedOrUnfixedLit(right, targetWidth, resizeFactory, this, this)
      }
    }

    abstract class Xor extends BinaryOperatorWidthableInputs with Widthable {
      def resizeFactory : Resize
      override def calcWidth(): Int = InferWidth.notResizableElseMax(this)
      override def normalizeInputs: Unit = {
        val targetWidth = getWidth
        left = InputNormalize.resizedOrUnfixedLit(left, targetWidth, resizeFactory, this, this)
        right = InputNormalize.resizedOrUnfixedLit(right, targetWidth, resizeFactory, this, this)
      }
    }

    abstract class Add extends BinaryOperatorWidthableInputs with Widthable{
      def resizeFactory : Resize
      override def calcWidth(): Int = InferWidth.notResizableElseMax(this)
      override def normalizeInputs: Unit = {
        val targetWidth = getWidth
        left = InputNormalize.resize(left, targetWidth, resizeFactory)
        right = InputNormalize.resize(right, targetWidth, resizeFactory)
      }
    }

    abstract class Sub extends BinaryOperatorWidthableInputs with Widthable{
      def resizeFactory : Resize
      override def calcWidth(): Int = InferWidth.notResizableElseMax(this)
      override def normalizeInputs: Unit = {
        val targetWidth = getWidth
        left = InputNormalize.resize(left, targetWidth, resizeFactory)
        right = InputNormalize.resize(right, targetWidth, resizeFactory)
      }
    }

    abstract class Mul extends BinaryOperatorWidthableInputs with Widthable{
      def getLiteralFactory : (BigInt, Int) => Expression
      override def calcWidth(): Int = left.getWidth + right.getWidth
      override def simplifyNode: Expression = {SymplifyNode.binaryInductZeroWithOtherWidth(getLiteralFactory)(this)}
    }

    abstract class Div extends BinaryOperatorWidthableInputs with Widthable{
      override def calcWidth(): Int = left.getWidth
    }

    abstract class Mod extends BinaryOperatorWidthableInputs with Widthable{
      override def calcWidth(): Int = left.getWidth
    }

    abstract class Equal extends BinaryOperatorWidthableInputs with ScalaLocated{
      override def getTypeObject = TypeBool
      override def normalizeInputs: Unit
      override def simplifyNode: Expression = {SymplifyNode.binaryThatIfBoth(new BoolLiteral(true))(this)}
    }

    abstract class NotEqual extends BinaryOperatorWidthableInputs with ScalaLocated{
      override def getTypeObject = TypeBool
      override def normalizeInputs: Unit
      override def simplifyNode: Expression = {SymplifyNode.binaryThatIfBoth(new BoolLiteral(false))(this)}
    }

    trait ShiftOperator
    abstract class ShiftRightByInt(val shift : Int) extends ConstantOperatorWidthableInputs with Widthable with ShiftOperator{
      assert(shift >= 0)
      override def calcWidth(): Int = Math.max(0, source.getWidth - shift)
    }

    abstract class ShiftRightByUInt extends BinaryOperatorWidthableInputs with Widthable with ShiftOperator{
      override def calcWidth(): Int = left.getWidth

      override def simplifyNode: Expression = if(right.getWidth == 0) left else this
    }

    abstract class ShiftLeftByInt(val shift : Int) extends ConstantOperatorWidthableInputs with Widthable with ShiftOperator{
      assert(shift >= 0)
      override def calcWidth(): Int = source.getWidth + shift
      def getLiteralFactory : (BigInt, Int) => BitVectorLiteral
      override def simplifyNode: Expression = {
        if(source.getWidth == 0){
          getLiteralFactory(0, this.getWidth)
        } else {
          this
        }
      }
    }

    abstract class ShiftLeftByUInt extends BinaryOperatorWidthableInputs with Widthable with ShiftOperator{
      override def calcWidth(): Int = left.getWidth + (1 << right.getWidth) - 1
      def getLiteralFactory : (BigInt, Int) => BitVectorLiteral
      override def simplifyNode: Expression = {
        if(left.getWidth == 0){
          getLiteralFactory(0, this.getWidth)
        }else if(right.getWidth == 0){
          left
        }else{
          this
        }
      }
    }


    abstract class ShiftRightByIntFixedWidth(val shift : Int) extends ConstantOperatorWidthableInputs with Widthable with ShiftOperator{
      assert(shift >= 0)
      override def calcWidth(): Int = source.getWidth
    }

    abstract class ShiftLeftByIntFixedWidth(val shift : Int) extends ConstantOperatorWidthableInputs with Widthable with ShiftOperator{
      assert(shift >= 0)
      override def calcWidth(): Int = source.getWidth

    }

    abstract class ShiftLeftByUIntFixedWidth extends BinaryOperatorWidthableInputs with Widthable with ShiftOperator{
      override def calcWidth(): Int = left.getWidth
      override def simplifyNode: Expression = if(right.getWidth == 0) left else this
    }
  }

  object Bits{
    class Cat extends BinaryOperatorWidthableInputs with Widthable{
      override def getTypeObject = TypeBits
      override def opName: String = "Bits ## Bits"
      override def calcWidth(): Int = left.getWidth + right.getWidth
      override def simplifyNode: Expression = {SymplifyNode.binaryTakeOther(this)}
    }

    class Not extends UnaryOperatorWidthableInputs{
      override def getTypeObject = TypeBits
      override def opName: String = "~ Bits"
      override def calcWidth(): Int = source.getWidth
    }

    class And extends BitVector.And{
      override def getTypeObject = TypeBits
      override def opName: String = "Bits & Bits"
      def resizeFactory : Resize = new ResizeBits
    }

    class Or extends BitVector.Or{
      override def getTypeObject = TypeBits
      override def opName: String = "Bits | Bits"
      def resizeFactory : Resize = new ResizeBits
    }

    class Xor extends BitVector.Xor{
      override def getTypeObject = TypeBits
      override def opName: String = "Bits ^ Bits"
      def resizeFactory : Resize = new ResizeBits
    }

    class Equal extends BitVector.Equal{
      override def normalizeInputs: Unit = {
        val targetWidth = InferWidth.notResizableElseMax(this)
        left = InputNormalize.resizedOrUnfixedLit(left, targetWidth, new ResizeBits, right, this)
        right = InputNormalize.resizedOrUnfixedLit(right, targetWidth, new ResizeBits, left, this)
      }
      override def opName: String = "Bits === Bits"
    }

    class NotEqual extends BitVector.NotEqual{
      override def normalizeInputs: Unit = {
        val targetWidth = InferWidth.notResizableElseMax(this)
        left = InputNormalize.resizedOrUnfixedLit(left, targetWidth, new ResizeBits, right, this)
        right = InputNormalize.resizedOrUnfixedLit(right, targetWidth, new ResizeBits, left, this)
      }
      override def opName: String = "Bits =/= Bits"
    }

    class ShiftRightByInt(shift : Int) extends BitVector.ShiftRightByInt(shift){
      override def getTypeObject = TypeBits
      override def opName: String = "Bits >> Int"
    }

    class ShiftRightByUInt extends BitVector.ShiftRightByUInt{
      override def getTypeObject = TypeBits
      override def opName: String = "Bits >> UInt"
    }

    class ShiftLeftByInt(shift : Int) extends BitVector.ShiftLeftByInt(shift){
      override def getTypeObject = TypeBits
      override def opName: String = "Bits << Int"
      override def getLiteralFactory : (BigInt, Int) => BitVectorLiteral = BitsLiteral.apply
    }

    class ShiftLeftByUInt extends BitVector.ShiftLeftByUInt{
      override def getTypeObject = TypeBits
      override def opName: String = "Bits << UInt"
      override def getLiteralFactory : (BigInt, Int) => BitVectorLiteral = BitsLiteral.apply
    }

    class ShiftRightByIntFixedWidth(shift : Int) extends BitVector.ShiftRightByIntFixedWidth(shift){
      override def getTypeObject = TypeBits
      override def opName: String = "Bits |>> Int"
    }

    class ShiftLeftByIntFixedWidth(shift : Int) extends BitVector.ShiftLeftByIntFixedWidth(shift){
      override def getTypeObject = TypeBits
      override def opName: String = "Bits |<< Int"
    }

    class ShiftLeftByUIntFixedWidth extends BitVector.ShiftLeftByUIntFixedWidth{
      override def getTypeObject = TypeBits
      override def opName: String = "Bits |<< UInt"
    }
  }


  object UInt{
    class Not extends UnaryOperatorWidthableInputs{
      override def getTypeObject = TypeUInt
      override def opName: String = "~ UInt"
      override def calcWidth(): Int = source.getWidth
    }

    class And extends BitVector.And{
      override def getTypeObject = TypeUInt
      override def opName: String = "UInt & UInt"
      def resizeFactory : Resize = new ResizeUInt
    }

    class Or extends BitVector.Or{
      override def getTypeObject = TypeUInt
      override def opName: String = "UInt | UInt"
      def resizeFactory : Resize = new ResizeUInt
    }

    class Xor extends BitVector.Xor{
      override def getTypeObject = TypeUInt
      override def opName: String = "UInt ^ UInt"
      def resizeFactory : Resize = new ResizeUInt
    }

    class Add extends BitVector.Add{
      override def getTypeObject = TypeUInt
      override def opName: String = "UInt + UInt"
      def resizeFactory : Resize = new ResizeUInt
    }

    class Sub extends BitVector.Sub{
      override def getTypeObject = TypeUInt
      override def opName: String = "UInt - UInt"
      def resizeFactory : Resize = new ResizeUInt
    }

    class Mul extends BitVector.Mul{
      override def getTypeObject = TypeUInt
      override def opName: String = "UInt * UInt"
      override def getLiteralFactory: (BigInt, Int) => Expression = UIntLiteral.apply
    }

    class Div extends BitVector.Div{
      override def getTypeObject = TypeUInt
      override def opName: String = "UInt / UInt"
    }

    class Mod extends BitVector.Mod{
      override def getTypeObject = TypeUInt
      override def opName: String = "UInt % UInt"
    }

    class Smaller extends BinaryOperatorWidthableInputs{
      override def getTypeObject = TypeBool
      override def opName: String = "UInt < UInt"
      override def normalizeInputs: Unit = {
        val targetWidth = InferWidth.notResizableElseMax(this)
        left = InputNormalize.resize(left, targetWidth, new ResizeUInt)
        right = InputNormalize.resize(right, targetWidth, new ResizeUInt)
      }
    }

    class SmallerOrEqual extends BinaryOperatorWidthableInputs{
      override def getTypeObject = TypeBool
      override def opName: String = "UInt <= UInt"
      override def normalizeInputs: Unit = {
        val targetWidth = InferWidth.notResizableElseMax(this)
        left = InputNormalize.resize(left, targetWidth, new ResizeUInt)
        right = InputNormalize.resize(right, targetWidth, new ResizeUInt)
      }
    }

    class Equal extends BitVector.Equal{
      override def opName: String = "UInt === UInt"
      override def normalizeInputs: Unit = {
        val targetWidth = InferWidth.notResizableElseMax(this)
        left = InputNormalize.resize(left, targetWidth, new ResizeUInt)
        right = InputNormalize.resize(right, targetWidth, new ResizeUInt)
      }
    }

    class NotEqual extends BitVector.NotEqual{
      override def opName: String = "UInt =/= UInt"
      override def normalizeInputs: Unit = {
        val targetWidth = InferWidth.notResizableElseMax(this)
        left = InputNormalize.resize(left, targetWidth, new ResizeUInt)
        right = InputNormalize.resize(right, targetWidth, new ResizeUInt)
      }
    }

    class ShiftRightByInt(shift : Int) extends BitVector.ShiftRightByInt(shift){
      override def getTypeObject = TypeUInt
      override def opName: String = "UInt >> Int"
    }

    class ShiftRightByUInt extends BitVector.ShiftRightByUInt{
      override def getTypeObject = TypeUInt
      override def opName: String = "UInt >> UInt"
    }

    class ShiftLeftByInt(shift : Int) extends BitVector.ShiftLeftByInt(shift){
      override def getTypeObject = TypeUInt
      override def opName: String = "UInt << Int"
      override def getLiteralFactory : (BigInt, Int) => BitVectorLiteral = UIntLiteral.apply
    }

    class ShiftLeftByUInt extends BitVector.ShiftLeftByUInt{
      override def getTypeObject = TypeUInt
      override def opName: String = "UInt << UInt"
      override def getLiteralFactory : (BigInt, Int) => BitVectorLiteral = UIntLiteral.apply
    }

    class ShiftRightByIntFixedWidth(shift : Int) extends BitVector.ShiftRightByIntFixedWidth(shift){
      override def getTypeObject = TypeUInt
      override def opName: String = "UInt |>> Int"
    }

    class ShiftLeftByIntFixedWidth(shift : Int) extends BitVector.ShiftLeftByIntFixedWidth(shift){
      override def getTypeObject = TypeUInt
      override def opName: String = "UInt |<< Int"
    }

    class ShiftLeftByUIntFixedWidth extends BitVector.ShiftLeftByUIntFixedWidth{
      override def getTypeObject = TypeUInt
      override def opName: String = "UInt |<< UInt"
    }
  }

  object SInt{
    class Not extends UnaryOperatorWidthableInputs with Widthable{
      override def getTypeObject = TypeSInt
      override def opName: String = "~ SInt"
      override def calcWidth(): Int = source.getWidth
    }

    class Minus extends UnaryOperatorWidthableInputs with Widthable{
      override def getTypeObject = TypeSInt
      override def opName: String = "- SInt"
      override def calcWidth(): Int = source.getWidth
    }

    class And extends BitVector.And{
      override def getTypeObject = TypeSInt
      override def opName: String = "SInt & SInt"
      def resizeFactory : Resize = new ResizeSInt
    }

    class Or extends BitVector.Or{
      override def getTypeObject = TypeSInt
      override def opName: String = "SInt | SInt"
      def resizeFactory : Resize = new ResizeSInt
    }

    class Xor extends BitVector.Xor{
      override def getTypeObject = TypeSInt
      override def opName: String = "SInt ^ SInt"
      def resizeFactory : Resize = new ResizeSInt
    }

    class Add extends BitVector.Add{
      override def getTypeObject = TypeSInt
      override def opName: String = "SInt + SInt"
      def resizeFactory : Resize = new ResizeSInt
    }

    class Sub extends BitVector.Sub{
      override def getTypeObject = TypeSInt
      override def opName: String = "SInt - SInt"
      def resizeFactory : Resize = new ResizeSInt
    }

    class Mul extends BitVector.Mul{
      override def getTypeObject = TypeSInt
      override def opName: String = "SInt * SInt"
      override def getLiteralFactory: (BigInt, Int) => Expression = SIntLiteral.apply
    }

    class Div extends BitVector.Div{
      override def getTypeObject = TypeSInt
      override def opName: String = "SInt / SInt"
    }

    class Mod extends BitVector.Mod{
      override def getTypeObject = TypeSInt
      override def opName: String = "SInt % SInt"
    }

    class Smaller extends BinaryOperatorWidthableInputs{
      override def getTypeObject = TypeBool
      override def opName: String = "SInt < SInt"
      override def normalizeInputs: Unit = {
        val targetWidth = InferWidth.notResizableElseMax(this)
        left = InputNormalize.resize(left, targetWidth, new ResizeSInt)
        right = InputNormalize.resize(right, targetWidth, new ResizeSInt)
      }
    }

    class SmallerOrEqual extends BinaryOperatorWidthableInputs{
      override def getTypeObject = TypeBool
      override def opName: String = "SInt <= SInt"
      override def normalizeInputs: Unit = {
        val targetWidth = InferWidth.notResizableElseMax(this)
        left = InputNormalize.resize(left, targetWidth, new ResizeSInt)
        right = InputNormalize.resize(right, targetWidth, new ResizeSInt)
      }
    }

    class Equal extends BitVector.Equal{
      override def opName: String = "SInt === SInt"
      override def normalizeInputs: Unit = {
        val targetWidth = InferWidth.notResizableElseMax(this)
        left = InputNormalize.resize(left, targetWidth, new ResizeSInt)
        right = InputNormalize.resize(right, targetWidth, new ResizeSInt)
      }
    }

    class NotEqual extends BitVector.NotEqual{
      override def opName: String = "SInt =/= SInt"
      override def normalizeInputs: Unit = {
        val targetWidth = InferWidth.notResizableElseMax(this)
        left = InputNormalize.resize(left, targetWidth, new ResizeSInt)
        right = InputNormalize.resize(right, targetWidth, new ResizeSInt)
      }
    }

    class ShiftRightByInt(shift : Int) extends BitVector.ShiftRightByInt(shift){
      override def getTypeObject = TypeSInt
      override def opName: String = "SInt >> Int"
    }

    class ShiftRightByUInt extends BitVector.ShiftRightByUInt{
      override def getTypeObject = TypeSInt
      override def opName: String = "SInt >> UInt"
    }

    class ShiftLeftByInt(shift : Int) extends BitVector.ShiftLeftByInt(shift){
      override def getTypeObject = TypeSInt
      override def opName: String = "SInt << Int"
      override def getLiteralFactory : (BigInt, Int) => BitVectorLiteral = SIntLiteral.apply
    }

    class ShiftLeftByUInt extends BitVector.ShiftLeftByUInt{
      override def getTypeObject = TypeSInt
      override def opName: String = "SInt << UInt"
      override def getLiteralFactory : (BigInt, Int) => BitVectorLiteral = SIntLiteral.apply
    }

    class ShiftRightByIntFixedWidth(shift : Int) extends BitVector.ShiftRightByIntFixedWidth(shift){
      override def getTypeObject = TypeSInt
      override def opName: String = "SInt |>> Int"
    }

    class ShiftLeftByIntFixedWidth(shift : Int) extends BitVector.ShiftLeftByIntFixedWidth(shift){
      override def getTypeObject = TypeSInt
      override def opName: String = "SInt |<< Int"
    }

    class ShiftLeftByUIntFixedWidth extends BitVector.ShiftLeftByUIntFixedWidth{
      override def getTypeObject = TypeSInt
      override def opName: String = "SInt |<< UInt"
    }
  }

  object Enum{
    class Equal(enumDef : SpinalEnum) extends BinaryOperator with InferableEnumEncodingImpl{
      override def getTypeObject: Any = TypeBool

      override def opName: String = "Enum === Enum"
      override def normalizeInputs: Unit = {InputNormalize.enumImpl(this)}

      override type T = Expression with EnumEncoded
      override private[core] def getDefaultEncoding(): SpinalEnumEncoding = enumDef.defaultEncoding
      override def getDefinition: SpinalEnum = enumDef
    }

    class NotEqual(enumDef : SpinalEnum) extends BinaryOperator with InferableEnumEncodingImpl{
      override def getTypeObject: Any = TypeBool
      override def opName: String = "Enum =/= Enum"
      override def normalizeInputs: Unit = {InputNormalize.enumImpl(this)}

      override type T = Expression with EnumEncoded
      override private[core] def getDefaultEncoding(): SpinalEnumEncoding = enumDef.defaultEncoding
      override def getDefinition: SpinalEnum = enumDef
    }

  }
}

abstract class Modifier extends Expression {

}


abstract class Cast extends Modifier {
  type T <: Expression
  var input : T = null.asInstanceOf[T]

  override def remapExpressions(func: (Expression) => Expression): Unit = input = func(input).asInstanceOf[T]
  override def foreachExpression(func: (Expression) => Unit): Unit = func(input)
}

abstract class CastBitVectorToBitVector extends Cast with Widthable{
  override type T <: Expression with WidthProvider
  override private[core] def calcWidth: Int = input.getWidth
}

class CastSIntToBits extends CastBitVectorToBitVector{
  override def getTypeObject = TypeBits
  override def opName: String = "SInt -> Bits"
}
class CastUIntToBits extends CastBitVectorToBitVector{
  override def getTypeObject = TypeBits
  override def opName: String = "UInt -> Bits"
}
class CastBitsToUInt extends CastBitVectorToBitVector{
  override def getTypeObject = TypeUInt
  override def opName: String = "Bits -> UInt"
}
class CastSIntToUInt extends CastBitVectorToBitVector{
  override def getTypeObject = TypeUInt
  override def opName: String = "SInt -> UInt"
}
class CastBitsToSInt extends CastBitVectorToBitVector{
  override def getTypeObject = TypeSInt
  override def opName: String = "Bits -> SInt"
}
class CastUIntToSInt extends CastBitVectorToBitVector{
  override def getTypeObject = TypeSInt
  override def opName: String = "UInt -> SInt"
}
class CastBoolToBits extends Cast with Widthable{
  override def getTypeObject = TypeBits
  override def opName: String = "Bits -> Bits"
  override private[core] def calcWidth: Int = 1
}

class CastEnumToBits extends Cast with Widthable{
  override type T <: Expression with EnumEncoded
  override def opName: String = "Enum -> Bits"
  override private[core] def calcWidth: Int = input.getEncoding.getWidth(input.getDefinition)
  override def getTypeObject: Any = TypeBits
}

class CastBitsToEnum(val enumDef: SpinalEnum) extends Cast with InferableEnumEncodingImpl{
  override type T <: Expression with WidthProvider
  override def opName: String = "Bits -> Enum"
  override private[core] def getDefaultEncoding(): SpinalEnumEncoding = enumDef.defaultEncoding
  override def getDefinition: SpinalEnum = enumDef

  override def normalizeInputs: Unit = {
    input = InputNormalize.resizedOrUnfixedLit(input, getEncoding.getWidth(enumDef), new ResizeBits, this, this).asInstanceOf[T]
  }

  override def getTypeObject: Any = TypeEnum
}

class CastEnumToEnum(enumDef: SpinalEnum) extends Cast with  InferableEnumEncodingImpl{
  override type T <: Expression with EnumEncoded
  override def opName: String = "Enum -> Enum"

  override private[core] def getDefaultEncoding(): SpinalEnumEncoding = enumDef.defaultEncoding
  override def getDefinition: SpinalEnum = enumDef
  override def getTypeObject: Any = TypeEnum
}



abstract class Multiplexer extends Modifier {
  type T <: Expression
  var cond      : Expression = null
  var whenTrue  : T = null.asInstanceOf[T]
  var whenFalse : T = null.asInstanceOf[T]

  override def remapExpressions(func: (Expression) => Expression): Unit = {
    cond = func(cond)
    whenTrue = func(whenTrue).asInstanceOf[T]
    whenFalse = func(whenFalse).asInstanceOf[T]
  }
  override def foreachExpression(func: (Expression) => Unit): Unit = {
    func(cond)
    func(whenTrue)
    func(whenFalse)
  }
}

abstract class MultiplexedWidthable extends Multiplexer with Widthable{
  override type T = Expression with WidthProvider
  override def calcWidth: Int = InferWidth.notResizableElseMax(this)
}

class MultiplexerBool extends Multiplexer{
  override def getTypeObject: Any = TypeBool
  override def opName: String = "mux(B,B,B)"
}
class MultiplexerBits extends MultiplexedWidthable {
  override def getTypeObject: Any = TypeBits
  override def opName: String = "mux(B,b,b)"
  override def normalizeInputs: Unit = {
    val targetWidth = getWidth
    whenTrue = InputNormalize.resizedOrUnfixedLit(whenTrue, targetWidth, new ResizeBits, this, this)
    whenFalse = InputNormalize.resizedOrUnfixedLit(whenFalse, targetWidth, new ResizeBits, this, this)
  }
}
class MultiplexerUInt extends MultiplexedWidthable{
  override def getTypeObject: Any = TypeUInt
  override def opName: String = "mux(B,u,u)"
  override def normalizeInputs: Unit = {
    val targetWidth = getWidth
    whenTrue = InputNormalize.resize(whenTrue, targetWidth, new ResizeUInt)
    whenFalse = InputNormalize.resize(whenFalse, targetWidth, new ResizeUInt)
  }
}
class MultiplexerSInt extends MultiplexedWidthable{
  override def getTypeObject: Any = TypeSInt
  override def opName: String = "mux(B,s,s)"
  override def normalizeInputs: Unit = {
    val targetWidth = getWidth
    whenTrue = InputNormalize.resize(whenTrue, targetWidth, new ResizeSInt)
    whenFalse = InputNormalize.resize(whenFalse, targetWidth, new ResizeSInt)
  }
}
class MultiplexerEnum(enumDef : SpinalEnum) extends Multiplexer with InferableEnumEncodingImpl{
  override type T = Expression with EnumEncoded
  override def opName: String = "mux(B,e,e)"
  override def getDefinition: SpinalEnum = enumDef
  override private[core] def getDefaultEncoding(): SpinalEnumEncoding = enumDef.defaultEncoding
  override def normalizeInputs: Unit = {
    InputNormalize.enumImpl(this)
  }
  override def getTypeObject: Any = TypeEnum
}


private[spinal] object Multiplex {

  def baseType[T <: BaseType](sel: Bool, whenTrue: T, whenFalse: T): Multiplexer = {
    whenTrue.newMultiplexer(sel, whenTrue, whenFalse)
  }


  def complexData[T <: Data](sel: Bool, whenTrue: T, whenFalse: T): T = {
    val outType = if (whenTrue.getClass.isAssignableFrom(whenFalse.getClass)) whenTrue
    else if (whenFalse.getClass.isAssignableFrom(whenTrue.getClass)) whenFalse
    else throw new Exception("can't mux that")


    val muxOut = weakCloneOf(outType)
    val muxInTrue = cloneOf(muxOut)
    val muxInFalse = cloneOf(muxOut)


    muxInTrue := whenTrue
    muxInFalse := whenFalse


    for ((out, t,  f) <- (muxOut.flatten, muxInTrue.flatten, muxInFalse.flatten).zipped) {
      if (t == null) SpinalError("Create a mux with incompatible true input type")
      if (f == null) SpinalError("Create a mux with incompatible false input type")

      out.assignFrom(Multiplex.baseType(sel, t.setAsTypeNode(), f.setAsTypeNode()))
      out.setAsTypeNode()
    }
    muxOut
  }
}


abstract class SubAccess extends Modifier{
  //  def finalTarget : NameableExpression
  def getBitVector: Expression
}

abstract class BitVectorBitAccessFixed extends SubAccess with ScalaLocated {
  var source : Expression with WidthProvider = null
  var bitId : Int = -1

  override def getBitVector: Expression = source

  override def normalizeInputs: Unit = {
    if (bitId < 0 || bitId >= source.getWidth) {
      PendingError(s"Static bool extraction (bit ${bitId}) is outside the range (${source.getWidth - 1} downto 0) of ${source} at\n${getScalaLocationLong}")
    }
  }

  override def remapExpressions(func: (Expression) => Expression): Unit = {
    source = func(source).asInstanceOf[Expression with WidthProvider]
  }

  override def foreachExpression(func: (Expression) => Unit): Unit = {
    func(source)
  }

  //  override def checkInferedWidth: Unit = {
  //    if (bitId < 0 || bitId >= getBitVector.getWidth) {
  //      PendingError(s"Static bool extraction (bit ${bitId}) is outside the range (${getBitVector.getWidth - 1} downto 0) of ${getBitVector} at\n${getScalaLocationLong}")
  //    }
  //  }
  //
  //  override private[core] def getOutToInUsage(inputId: Int, outHi: Int, outLo: Int): (Int, Int) = inputId match{
  //    case 0 =>
  //      if(outHi >= 0 && outLo == 0)
  //        (bitId, bitId)
  //      else
  //        (-1,0)
  //  }
  //  def getParameterNodes: List[Node] = Nil
}

class BitsBitAccessFixed extends BitVectorBitAccessFixed{
  override def getTypeObject = TypeBool
  override def opName: String = "Bits(Int)"
}
class UIntBitAccessFixed extends BitVectorBitAccessFixed{
  override def getTypeObject = TypeBool
  override def opName: String = "UInt(Int)"
}
class SIntBitAccessFixed extends BitVectorBitAccessFixed{
  override def getTypeObject = TypeBool
  override def opName: String = "SInt(Int)"
}

abstract class BitVectorBitAccessFloating extends SubAccess with ScalaLocated {
  var source  : Expression with WidthProvider = null
  var bitId  : Expression with WidthProvider = null
  override def getBitVector: Expression = source

  override def normalizeInputs: Unit = {
    if(source.getWidth == 0){
      PendingError(s"Can't access ${source} bits, as it has none")
    }
  }

  def bitVectorBitAccessFixedFactory : BitVectorBitAccessFixed
  override def simplifyNode: Expression = {
    if(bitId.getWidth == 0){
      val access = bitVectorBitAccessFixedFactory
      access.bitId = 0
      access.source = source
      access
    }else{
      this
    }
  }

  override def remapExpressions(func: (Expression) => Expression): Unit = {
    source = func(source).asInstanceOf[Expression with WidthProvider]
    bitId = func(bitId).asInstanceOf[Expression with WidthProvider]
  }

  override def foreachExpression(func: (Expression) => Unit): Unit = {
    func(source)
    func(bitId)
  }

  //  def getParameterNodes: List[Node] = getInput(1) :: Nil
  //  override private[core] def getOutToInUsage(inputId: Int, outHi: Int, outLo: Int): (Int, Int) = inputId match{
  //    case 0 =>
  //      if(outHi >= 0 && outLo == 0)
  //        (Math.min(getBitVector.getWidth-1,(1 << Math.min(20,bitId.getWidth)) - 1), 0)
  //      else
  //        (-1,0)
  //    case 1 =>
  //      if(outHi >= 0 && outLo == 0)
  //        (getBitId.getWidth-1,0)
  //      else
  //        (-1,0)
  //  }
}

class BitsBitAccessFloating extends BitVectorBitAccessFloating{
  override def getTypeObject = TypeBool
  override def opName: String = "Bits(UInt)"
  override def bitVectorBitAccessFixedFactory: BitVectorBitAccessFixed = new BitsBitAccessFixed
}
class UIntBitAccessFloating extends BitVectorBitAccessFloating{
  override def getTypeObject = TypeBool
  override def opName: String = "UInt(UInt)"
  override def bitVectorBitAccessFixedFactory: BitVectorBitAccessFixed = new UIntBitAccessFixed
}
class SIntBitAccessFloating extends BitVectorBitAccessFloating{
  override def getTypeObject = TypeBool
  override def opName: String = "SInt(UInt)"
  override def bitVectorBitAccessFixedFactory: BitVectorBitAccessFixed = new SIntBitAccessFixed
}


abstract class BitVectorRangedAccessFixed extends SubAccess with WidthProvider{
  var source : Expression with WidthProvider = null
  var hi, lo = -1
  override def getBitVector: Expression = source

  override def normalizeInputs: Unit = {
    if (hi >= source.getWidth || lo < 0) {
      PendingError(s"Static bits extraction ($hi downto $lo) is outside the range (${source.getWidth - 1} downto 0) of ${source} at\n${getScalaLocationLong}")
    }
  }

  def checkHiLo : Unit = if (hi - lo < -1)
    SpinalError(s"Static bits extraction with a negative size ($hi downto $lo)")

  override def getWidth: Int = hi - lo + 1

  override def remapExpressions(func: (Expression) => Expression): Unit = {
    source = func(source).asInstanceOf[Expression with Widthable]
  }

  override def foreachExpression(func: (Expression) => Unit): Unit = {
    func(source)
  }


  //  override private[core] def getOutToInUsage(inputId: Int, outHi: Int, outLo: Int): (Int, Int) = inputId match{
  //    case 0 => (lo+outHi, lo+outLo)
  //  }
}

class BitsRangedAccessFixed extends BitVectorRangedAccessFixed{
  override def getTypeObject = TypeBits
  override def opName: String = "Bits(Int downto Int)"
}
class UIntRangedAccessFixed extends BitVectorRangedAccessFixed{
  override def getTypeObject = TypeUInt
  override def opName: String = "UInt(Int downto Int)"
}
class SIntRangedAccessFixed extends BitVectorRangedAccessFixed{
  override def getTypeObject = TypeSInt
  override def opName: String = "SInt(Int downto Int)"
}
//
////WHen used offset.dontSimplifyIt() Because it can appear at multipe location (o+bc-1 downto o)
abstract class BitVectorRangedAccessFloating extends SubAccess with WidthProvider {
  var size    : Int = -1
  var source  : Expression with WidthProvider = null
  var offset  : Expression with WidthProvider = null
  override def getBitVector: Expression = source

  override def getWidth: Int = size

  override def normalizeInputs: Unit = {
    if(source.getWidth < size){
      PendingError(s"Can't access ${source} bits, as it has less than $size")
    }
  }

  def bitVectorRangedAccessFixedFactory : BitVectorRangedAccessFixed
  override def simplifyNode: Expression = {
    if(offset.getWidth == 0){
      val access = bitVectorRangedAccessFixedFactory
      access.lo = 0
      access.hi = size-1
      access.source = source
      access
    }else{
      this
    }
  }

  override def remapExpressions(func: (Expression) => Expression): Unit = {
    source = func(source).asInstanceOf[Expression with WidthProvider]
    offset = func(offset).asInstanceOf[Expression with WidthProvider]
  }

  override def foreachExpression(func: (Expression) => Unit): Unit = {
    func(source)
    func(offset)
  }

  //  override private[core] def getOutToInUsage(inputId: Int, outHi: Int, outLo: Int): (Int, Int) = inputId match{
  //    case 0 =>
  //      if(outHi >= outLo) //Not exact
  //        (Math.min(getBitVector.getWidth-1,(1 << Math.min(20,offset.getWidth))+ size - 1), 0)
  //      else
  //        (-1,0)
  //    case 1 =>
  //      if(outHi >= outLo) //Not exact
  //        super.getOutToInUsage(inputId,outHi,outLo)
  //      else
  //        (-1,0)
  //    case 2 => (-1,0)
  //  }
}

class BitsRangedAccessFloating extends BitVectorRangedAccessFloating{
  override def getTypeObject = TypeBits
  override def opName: String = "Bits(UInt + Int downto UInt)"
  override def bitVectorRangedAccessFixedFactory: BitVectorRangedAccessFixed = new BitsRangedAccessFixed
}
class UIntRangedAccessFloating extends BitVectorRangedAccessFloating{
  override def getTypeObject = TypeUInt
  override def opName: String = "UInt(UInt + Int downto UInt)"
  override def bitVectorRangedAccessFixedFactory: BitVectorRangedAccessFixed = new UIntRangedAccessFixed
}
class SIntRangedAccessFloating extends BitVectorRangedAccessFloating{
  override def getTypeObject = TypeSInt
  override def opName: String = "SInt(UInt + Int downto UInt)"
  override def bitVectorRangedAccessFixedFactory: BitVectorRangedAccessFixed = new SIntRangedAccessFixed
}

////object AssignedBits {
////  def apply() = new AssignedBits
////  def apply(bitId: Int): AssignedBits = {
////    val ab = new AssignedBits
////    ab.add(new AssignedRange(bitId, bitId))
////    ab
////  }
////  def apply(hi: Int, lo: Int): AssignedBits = {
////    val ab = new AssignedBits
////    ab.add(new AssignedRange(hi, lo))
////    ab
////  }
////
////  def union(a: AssignedBits, b: AssignedBits): AssignedBits = {
////    val ret = AssignedBits()
////
////    ret.add(a)
////    ret.add(b)
////
////    ret
////  }
////
////
////  def intersect(a: AssignedBits, b: AssignedBits): AssignedBits = {
////    val ret = AssignedBits()
////    ret.value = a.value & b.value
////    ret
////  }
////
////
////}
//
object AssignedBits {
  //  def apply(bitId: Int): AssignedBits = {
  //    val ab = new AssignedBits
  //    ab.add(new AssignedRange(bitId, bitId))
  //    ab
  //  }
  //  def apply(hi: Int, lo: Int): AssignedBits = {
  //    val ab = new AssignedBits
  //    ab.add(new AssignedRange(hi, lo))
  //    ab
  //  }
  def union(a: AssignedBits, b: AssignedBits): AssignedBits = {
    val ret = new AssignedBits(a.width)

    ret.add(a)
    ret.add(b)

    ret
  }


  def intersect(a: AssignedBits, b: AssignedBits): AssignedBits = {
    val ret = a.clone()
    ret.intersect(b)
    ret
  }


  def intersect(a: AssignedRange, b: AssignedBits): AssignedBits = {
    val ret = b.clone()
    ret.intersect(a)
    ret
  }
  def intersect(a: AssignedBits, b: AssignedRange): AssignedBits = intersect(b,a)



}
//
object AssignedRange{
  def apply(hi : Int,lo : Int) = new AssignedRange(hi,lo)
  def apply(bit : Int) = new AssignedRange(bit,bit)
  def apply() = new AssignedRange(-1,-1)
}

class AssignedRange(val hi: Int, val lo: Int) {
  def toBigInt = ((BigInt(1) << (hi + 1 - lo)) - 1) << lo
  def toAssignedBits = {
    val ret = new AssignedBits(hi + 1)
    ret.add(this)
    ret
  }
}
////
////class AssignedBits(val width : Int) {
////  var value: BigInt = 0
////  override def clone() : AssignedBits = {
////    val ret = new AssignedBits(width)
////    ret.value = value
////    ret
////  }
////  def intersect(range: AssignedRange): Unit = {
////    value = value & range.toBigInt
////  }
////  def intersect(range: AssignedBits): Unit = {
////    value = value & range.value
////  }
////  def add(range: AssignedRange): Unit = {
////    value = value | range.toBigInt
////  }
////  def add(range: AssignedBits): Unit = {
////    value = value | range.value
////  }
////  def remove(range: AssignedRange): Unit = {
////    value = value &~ range.toBigInt
////  }
////  def remove(range: AssignedBits): Unit = {
////    value = value &~ range.value
////  }
////
////  def isEmpty = value == 0
////  def toBinaryString : String = value.toString(2)
////}
////
class AssignedBits(val width : Int) {
  def bitPerIndex = 32
  var value = new Array[Int]((width+bitPerIndex-1)/bitPerIndex)

  override def clone() : AssignedBits = {
    val ret = new AssignedBits(width)
    var idx = value.length
    while(idx != 0){
      idx -= 1
      ret.value(idx) = this.value(idx)
    }
    ret
  }

  def isIntersecting(range: AssignedRange): Boolean = {
    if(range.hi >= width)
      assert(false)
    var idx = value.length
    while(idx != 0){
      idx -= 1
      val hi = Math.min(range.hi - idx*bitPerIndex,bitPerIndex-1)
      val lo = Math.max(range.lo - idx*bitPerIndex,0)
      if(hi >= lo) {
        if((this.value(idx) & (((1l << (hi + 1)) - 1) - ((1l << lo) - 1)).toInt) != 0) {
          return true
        }
      }
    }
    return false
  }


  def clear(): Unit ={
    var idx = value.length
    while(idx != 0){
      idx -= 1
      value(idx) = 0
    }
  }

  def + (that : AssignedRange) : AssignedBits = {
    val ret = clone()
    ret.add(that)
    ret
  }

  def intersect(range: AssignedBits): AssignedBits = {
    assert(range.width == this.width)
    var idx = Math.min(value.length,range.value.length)
    while(idx != 0){
      idx -= 1
      this.value(idx) &= range.value(idx)
    }
    this
  }

  def add(range: AssignedBits): Unit = {
    assert(range.width == this.width)
    var idx = Math.min(value.length,range.value.length)
    while(idx != 0){
      idx -= 1
      this.value(idx) |= range.value(idx)
    }
  }
  def addChangeReturn(range: AssignedBits): Boolean = {
    assert(range.width == this.width)
    var idx = Math.min(value.length,range.value.length)
    var changed = false
    while(idx != 0){
      idx -= 1
      val currentValue = this.value(idx)
      val ored = currentValue | range.value(idx)
      changed |= ored != currentValue
      this.value(idx) = ored
    }
    return changed
  }
  def remove(range: AssignedBits): Unit = {
    assert(range.width == this.width)
    var idx = Math.min(value.length,range.value.length)
    while(idx != 0){
      idx -= 1
      this.value(idx) &= ~range.value(idx)
    }
  }
  def intersect(range: AssignedRange): Unit = {
    assert(range.hi < width)
    var idx = value.length
    while(idx != 0){
      idx -= 1
      val hi = Math.min(range.hi - idx*bitPerIndex,bitPerIndex-1)
      val lo = Math.max(range.lo - idx*bitPerIndex,0)
      if(hi >= lo)
        this.value(idx) &= (((1l << (hi+1))-1)-((1l << lo)-1)).toInt
    }
  }
  def add(hi: Int, lo: Int): Unit = {
    assert(hi < width)
    var idx = value.length
    while(idx != 0){
      idx -= 1
      val hiA = Math.min(hi - idx*bitPerIndex,bitPerIndex-1)
      val loA = Math.max(lo - idx*bitPerIndex,0)
      if(hiA >= loA)
        this.value(idx) |= (((1l << (hiA+1))-1)-((1l << loA)-1)).toInt
    }
  }
  def add(range: AssignedRange): Unit = add(range.hi, range.lo)
  def remove(range: AssignedRange): Unit = {
    assert(range.hi < width)
    var idx = value.length
    while(idx != 0){
      idx -= 1
      val hi = Math.min(range.hi - idx*bitPerIndex,bitPerIndex-1)
      val lo = Math.max(range.lo - idx*bitPerIndex,0)
      if(hi >= lo)
        this.value(idx) &= ~(((1l << (hi+1))-1)-((1l << lo)-1)).toInt
    }
  }

  def toBinaryString : String = {
    val strs = for((e,idx) <- value.zipWithIndex.reverseIterator) yield {
      val str = e.toBinaryString
      val eWidth = if(idx == value.length-1) width-idx*bitPerIndex else 32

      "0"*(eWidth-str.length) + str
    }
    strs.reduce(_ + "_" + _)
  }
  def isEmpty = value.foldLeft(true)((c,e) => c && (e == 0))
  def isFull : Boolean = {
    if(width == 0) return true
    var remainingBits = width
    var i = 0
    while(remainingBits > 0){
      if(remainingBits > 31) {
        if (value(i) != 0xFFFFFFFF) return false
      }else{
        if (value(i) !=  (1 << remainingBits)-1) return false
      }
      i += 1
      remainingBits -= 32
    }
    true
  }
}

abstract class AssignmentExpression extends Expression {
  def finalTarget: BaseType
  override def foreachDrivingExpression(func : (Expression) => Unit) : Unit
  override def remapDrivingExpressions(func: (Expression) => Expression): Unit
  def getMinAssignedBits: AssignedRange //Bit that are allwas assigned
  def getMaxAssignedBits: AssignedRange //Bit that are allwas assigned
  //  def getScopeBits: AssignedRange //Bit tht could be assigned
  //  def getOutBaseType: BaseType
  //
  //  def clone(out : Node) : this.type
}




abstract class BitVectorAssignmentExpression extends AssignmentExpression{
  def minimalTargetWidth : Int
}

object BitAssignmentFixed{
  def apply(out: BitVector, bitId: Int ) = {
    val ret = new BitAssignmentFixed
    ret.out = out
    ret.bitId = bitId
    ret
  }
}
class BitAssignmentFixed() extends BitVectorAssignmentExpression with ScalaLocated{
  var out: BitVector = null
  var bitId: Int = -1


  override def getTypeObject = TypeBool

  override def finalTarget: BaseType = out

  override def minimalTargetWidth: Int = bitId + 1

  override def normalizeInputs: Unit = {
    if (bitId < 0 || bitId >= out.getWidth) {
      PendingError(s"Static bool extraction (bit ${bitId}) is outside the range (${out.getWidth - 1} downto 0) of ${out} at\n${getScalaLocationLong}")
    }
  }

  override def foreachExpression(func: (Expression) => Unit): Unit = func(out)
  override def remapExpressions(func: (Expression) => Expression): Unit = {out = func(out).asInstanceOf[BitVector]}
  override def foreachDrivingExpression(func: (Expression) => Unit): Unit = {}
  override def remapDrivingExpressions(func: (Expression) => Expression): Unit = {}
  override def toString(): String = s"${out.toString()}[$bitId]"
  override def opName: String = "x(index) <="


  //  override def checkInferedWidth: Unit = {
  //    if (bitId < 0 || bitId >= out.getWidth) {
  //      PendingError(s"Static bool extraction (bit ${bitId}) is outside the range (${out.getWidth - 1} downto 0) of ${out} at\n${getScalaLocationLong}")
  //    }
  //  }
  override def getMinAssignedBits: AssignedRange = AssignedRange(bitId)
  override def getMaxAssignedBits: AssignedRange = AssignedRange(bitId)
  //  def getScopeBits: AssignedRange = getAssignedBits
  //  override private[core] def getOutToInUsage(inputId: Int, outHi: Int, outLo: Int): (Int, Int) = {
  //    if(outHi >= bitId && bitId >= outLo)
  //      (0,0)
  //    else
  //      (-1,0)
  //  }
  //  def getOutBaseType: BaseType = out
  //
  //  override def clone(out: Node): this.type = new BitAssignmentFixed(out.asInstanceOf[BitVector],in,bitId).asInstanceOf[this.type]

}


object RangedAssignmentFixed{
  def apply(out: BitVector,hi: Int,lo: Int): RangedAssignmentFixed ={
    val assign = new RangedAssignmentFixed
    assign.out = out
    assign.hi = hi
    assign.lo = lo
    assign
  }
}

class RangedAssignmentFixed() extends BitVectorAssignmentExpression with WidthProvider {
  var out: BitVector = null
  var hi = -1
  var lo = 0
  override def getWidth: Int = hi + 1 - lo
  override def finalTarget: BaseType = out
  override def minimalTargetWidth: Int = hi+1
  override def getTypeObject = out.getTypeObject
  override def normalizeInputs: Unit = {
    if (hi >= out.getWidth || lo < 0) {
      PendingError(s"Static bits assignment ($hi downto $lo) is outside the range (${out.getWidth - 1} downto 0) of ${out} at\n${getScalaLocationLong}")
      return
    }
  }

  override def foreachExpression(func: (Expression) => Unit): Unit = func(out)
  override def remapExpressions(func: (Expression) => Expression): Unit = {out = func(out).asInstanceOf[BitVector]}
  override def foreachDrivingExpression(func : (Expression) => Unit) : Unit = {}
  override def remapDrivingExpressions(func: (Expression) => Expression): Unit = {}
  override def toString(): String = s"${out.toString()}[$hi downto $lo]"
  override def opName: String = "x(hi:lo) <="



  override def getMinAssignedBits: AssignedRange = AssignedRange(hi, lo)
  override def getMaxAssignedBits: AssignedRange = AssignedRange(hi, lo)
  //  def getScopeBits: AssignedRange = getAssignedBits
  //  override private[core] def getOutToInUsage(inputId: Int, outHi: Int, outLo: Int): (Int, Int) = {
  //    val relativeLo = outLo-lo
  //    val relativeHi = outHi-lo
  //    if(relativeHi >= 0 && hi-lo >= relativeLo)
  //      super.getOutToInUsage(inputId,Math.min(relativeHi,hi-lo),Math.max(relativeLo,0))
  //    else
  //      (-1,0)
  //  }
  //  def getOutBaseType: BaseType = out
  //  override def clone(out: Node): this.type = new RangedAssignmentFixed(out.asInstanceOf[BitVector],in,hi,lo).asInstanceOf[this.type]
}


object BitAssignmentFloating{
  def apply(out: BitVector,bitId: UInt): BitAssignmentFloating ={
    val assign = new BitAssignmentFloating
    assign.out = out
    assign.bitId = bitId
    assign
  }
}
class BitAssignmentFloating() extends BitVectorAssignmentExpression{
  var out  : BitVector = null
  var bitId  : Expression with WidthProvider = null
  override def getTypeObject = TypeBool
  override def finalTarget: BaseType = out
  override def minimalTargetWidth: Int = 1 << Math.min(20,bitId.getWidth)
  override def foreachExpression(func: (Expression) => Unit): Unit = {
    func(out)
    func(bitId)
  }
  override def remapExpressions(func: (Expression) => Expression): Unit = {
    out = func(out).asInstanceOf[BitVector]
    bitId = func(bitId).asInstanceOf[Expression with WidthProvider]

  }
  override def foreachDrivingExpression(func: (Expression) => Unit): Unit = {
    func(bitId)
  }
  override def remapDrivingExpressions(func: (Expression) => Expression): Unit = {
    bitId = func(bitId).asInstanceOf[Expression with WidthProvider]
  }
  override def toString(): String = s"${out.toString()}[$bitId]"
  override def opName: String = "x(uIndex) <="

  override def simplifyNode: Expression = {
    if(bitId.getWidth == 0) {
      BitAssignmentFixed(out, 0)
    }else
      this
  }
  override def getMinAssignedBits: AssignedRange = AssignedRange()
  override def getMaxAssignedBits: AssignedRange = AssignedRange((1 << bitId.getWidth)-1, 0)
  //  def getScopeBits: AssignedRange = AssignedRange(Math.min(out.getWidth-1,(1 << Math.min(20,bitId.getWidth)) - 1), 0)
  //
  //  override private[core] def getOutToInUsage(inputId: Int, outHi: Int, outLo: Int): (Int, Int) = inputId match{
  //    case 0 =>
  //      if(outHi >= 0 && ((1 << Math.min(20,bitId.getWidth)) - 1) >= outLo)
  //        (0,0)
  //      else
  //        (-1,0)
  //    case 1 =>
  //      if(outHi >= 0 && ((1 << Math.min(20,bitId.getWidth)) - 1) >= outLo)
  //        super.getOutToInUsage(inputId,outHi,outLo)
  //      else
  //        (-1,0)
  //  }
  //  def getOutBaseType: BaseType = out
  //  override def clone(out: Node): this.type = new BitAssignmentFloating(out.asInstanceOf[BitVector],in_,bitId_).asInstanceOf[this.type]
}

object RangedAssignmentFloating{
  def apply(out: BitVector,offset: UInt,bitCount: Int): RangedAssignmentFloating ={
    val assign = new RangedAssignmentFloating
    assign.out = out
    assign.offset = offset
    assign.bitCount = bitCount
    assign
  }
}

class RangedAssignmentFloating() extends BitVectorAssignmentExpression with WidthProvider {
  var out  : BitVector = null
  var offset  : Expression with WidthProvider = null
  var bitCount : Int = -1
  override def getTypeObject = out.getTypeObject
  override def normalizeInputs: Unit = {
    if (out.getWidth < bitCount) {
      PendingError(s"Dynamic bits assignment of $bitCount bits is outside the range (${out.getWidth - 1} downto 0) of ${out} at\n${getScalaLocationLong}")
      return
    }
  }

  override def simplifyNode: Expression = {
    if(offset.getWidth == 0) {
      RangedAssignmentFixed(out, bitCount-1, 0)
    }else
      this
  }

  override def getWidth: Int = bitCount
  override def finalTarget: BaseType = out
  override def minimalTargetWidth: Int = 1 << Math.min(20,offset.getWidth) + bitCount
  override def foreachExpression(func: (Expression) => Unit): Unit = {
    func(out)
    func(offset)
  }
  override def remapExpressions(func: (Expression) => Expression): Unit = {
    out = func(out).asInstanceOf[BitVector]
    offset = func(offset).asInstanceOf[Expression with WidthProvider]

  }
  override def foreachDrivingExpression(func: (Expression) => Unit): Unit = {
    func(offset)
  }
  override def remapDrivingExpressions(func: (Expression) => Expression): Unit = {
    offset = func(offset).asInstanceOf[Expression with WidthProvider]
  }

  override def opName: String = "x(hi:lo) <="

  //  override def normalizeInputs: Unit = {
  //    InputNormalize.resizedOrUnfixedLit(this,0,bitCount.value)
  //  }
  //
  //
  //
  override def getMinAssignedBits: AssignedRange = AssignedRange()
  override def getMaxAssignedBits: AssignedRange = AssignedRange((1 << offset.getWidth)-1 + bitCount - 1, 0)

  //  def getScopeBits: AssignedRange = AssignedRange(Math.min(out.getWidth-1,(1 << Math.min(20,offset_.asInstanceOf[Node with WidthProvider].getWidth))+ bitCount.value - 1), 0) //TODO dirty offset_
  //  override private[core] def getOutToInUsage(inputId: Int, outHi: Int, outLo: Int): (Int, Int) = super.getOutToInUsage(inputId,outHi,outLo) //TODO
  //  def getOutBaseType: BaseType = out
  //  override def clone(out: Node): this.type = new RangedAssignmentFloating(out.asInstanceOf[BitVector],in_,offset_,bitCount).asInstanceOf[this.type]
}




object SwitchStatementKeyBool{
  def apply(cond : Expression): SwitchStatementKeyBool ={
    val ret = new SwitchStatementKeyBool
    ret.cond = cond
    ret
  }
}

class SwitchStatementKeyBool extends Expression{
  var cond : Expression = null

  override def opName: String = "is(b)"
  override def getTypeObject: Any = TypeBool
  override def remapExpressions(func: (Expression) => Expression): Unit = cond = func(cond)
  override def foreachExpression(func: (Expression) => Unit): Unit = func(cond)
}
class SwitchStatementElement(var keys : ArrayBuffer[Expression],var scopeStatement: ScopeStatement) extends ScalaLocated



trait Literal extends Expression {
  override def clone: this.type = ???
  final override def foreachExpression(func: (Expression) => Unit): Unit = {}
  final override def remapExpressions(func: (Expression) => Expression): Unit = {}
  private[core] def getBitsStringOn(bitCount : Int, poisonSymbol : Char) : String
  private[core] def getBitsStringOnNoPoison(bitCount : Int) : String = {
    require(!hasPoison)
    getBitsStringOn(bitCount,'?')
  }
  def getValue() : BigInt
  def hasPoison() : Boolean

  //  override def addAttribute(attribute: Attribute): Literal.this.type = addTag(attribute)
}

object BitsLiteral {
  def apply(value: BigInt, poisonMask : BigInt, specifiedBitCount: Int): BitsLiteral = {
    val valueBitCount = value.bitLength
    val poisonBitCount = if(poisonMask != null) poisonMask.bitLength else 0
    val minimalWidth = Math.max(poisonBitCount,valueBitCount)
    var bitCount = specifiedBitCount
    if (value < 0) throw new Exception("literal value is negative and can be represented")
    if (bitCount != -1) {
      if (minimalWidth > bitCount) throw new Exception("literal width specification is to small")
    } else {
      bitCount = minimalWidth
    }
    BitsLiteral(value, poisonMask, bitCount,specifiedBitCount != -1)
  }
  def apply[T <: BitVector](value: BigInt, specifiedBitCount: Int,on : T) : T ={
    on.assignFrom(apply(value, null, specifiedBitCount))
    on
  }
  def apply[T <: BitVector](value: BigInt, specifiedBitCount: Int) : BitsLiteral  = apply(value, null, specifiedBitCount)


  def apply(value: BigInt, poisonMask : BigInt, bitCount: Int ,hasSpecifiedBitCount : Boolean) = {
    val ret = new BitsLiteral
    ret.value = value
    ret.poisonMask = poisonMask
    ret.bitCount = bitCount
    ret.hasSpecifiedBitCount = hasSpecifiedBitCount
    ret
  }
}

object UIntLiteral {
  def apply(value: BigInt, poisonMask : BigInt, specifiedBitCount: Int): UIntLiteral = {
    val valueBitCount = value.bitLength
    val poisonBitCount = if(poisonMask != null) poisonMask.bitLength else 0
    val minimalWidth = Math.max(poisonBitCount,valueBitCount)
    var bitCount = specifiedBitCount
    if (value < 0) throw new Exception("literal value is negative and can be represented")
    if (bitCount != -1) {
      if (minimalWidth > bitCount) throw new Exception("literal width specification is to small")
    } else {
      bitCount = minimalWidth
    }
    UIntLiteral(value, poisonMask, bitCount,specifiedBitCount != -1)
  }
  def apply[T <: BitVector](value: BigInt, specifiedBitCount: Int,on : T):T={
    on.assignFrom(apply(value, null, specifiedBitCount))
    on
  }
  def apply[T <: BitVector](value: BigInt, specifiedBitCount: Int) : UIntLiteral  = apply(value, null, specifiedBitCount)


  def apply(value: BigInt, poisonMask : BigInt, bitCount: Int ,hasSpecifiedBitCount : Boolean) = {
    val ret = new UIntLiteral
    ret.value = value
    ret.poisonMask = poisonMask
    ret.bitCount = bitCount
    ret.hasSpecifiedBitCount = hasSpecifiedBitCount
    ret
  }
}

object SIntLiteral{
  def apply(value: BigInt, poisonMask : BigInt, specifiedBitCount: Int): SIntLiteral = {
    val valueBitCount = value.bitLength + (if (value != 0) 1 else 0)
    val poisonBitCount = if(poisonMask != null) poisonMask.bitLength else 0
    val minimalWidth = Math.max(poisonBitCount,valueBitCount)
    var bitCount = specifiedBitCount
    if (bitCount != -1) {
      if (minimalWidth > bitCount ) throw new Exception("literal width specification is to small")
    } else {
      bitCount = minimalWidth
    }
    SIntLiteral(value, poisonMask, bitCount,specifiedBitCount != -1)
  }
  def apply[T <: BitVector](value: BigInt, specifiedBitCount: Int,on : T):T={
    on.assignFrom(apply(value,null,specifiedBitCount))
    on
  }
  def apply[T <: BitVector](value: BigInt, specifiedBitCount: Int) : SIntLiteral = apply(value, null, specifiedBitCount)


  def apply(value: BigInt, poisonMask : BigInt, bitCount: Int ,hasSpecifiedBitCount : Boolean) = {
    val ret = new SIntLiteral
    ret.value = value
    ret.poisonMask = poisonMask
    ret.bitCount = bitCount
    ret.hasSpecifiedBitCount = hasSpecifiedBitCount
    ret
  }
}


abstract class BitVectorLiteral() extends Literal with WidthProvider {
  var value: BigInt = null
  var poisonMask : BigInt = null
  var bitCount: Int = -1
  var hasSpecifiedBitCount : Boolean = true

  override def getWidth: Int = bitCount
  override def getValue(): BigInt = if(hasPoison) throw new Exception("Poisoned value") else value


  override def hasPoison() = poisonMask != null && poisonMask != 0

  override def getBitsStringOn(bitCount: Int, poisonSymbol : Char): String = {
    def makeIt(fillWith : Boolean) : String = {
      val str = new StringBuilder()
      val unsignedValue = if(value >= 0) value else ((BigInt(1) << bitCount) + value)
      val unsignedLength = unsignedValue.bitLength
      val poisonLength = if(poisonMask != null) poisonMask.bitLength else 0

      assert(bitCount >= unsignedLength)
      assert(bitCount >= poisonLength)

      val filling = if(fillWith) '1' else '0'
      for(i <- 0 until bitCount-unsignedLength) str += filling

      for(i <-  unsignedLength - 1 to 0 by - 1){
        str += (if(unsignedValue.testBit(i)) '1' else '0')
      }

      for(i <- poisonLength-1 to 0 by -1 if(poisonMask.testBit(i))){
        str(bitCount-i-1) = poisonSymbol
      }

      str.toString()
    }

    makeIt(isSignedKind && value < 0)
  }


  def minimalValueBitWidth : Int = {
    val pureWidth = value.bitLength + (if(isSignedKind && value != 0) 1 else 0)
    if(hasPoison) Math.max(poisonMask.bitLength,pureWidth) else pureWidth
  }

  def isSignedKind : Boolean

  override def toString: String =  s"${'"'}${getBitsStringOn(bitCount, 'x')}${'"'} $bitCount bits)"
}

class BitsLiteral extends BitVectorLiteral{
  override def getTypeObject = TypeBits
  override def isSignedKind: Boolean = false
  override def clone(): this.type = BitsLiteral(value, poisonMask, bitCount,hasSpecifiedBitCount).asInstanceOf[this.type]
  override def opName: String = "B\"xxx\""
  override def toString = "(B" + super.toString
}

class UIntLiteral extends BitVectorLiteral{
  override def getTypeObject = TypeUInt
  override def isSignedKind: Boolean = false
  override def clone(): this.type = UIntLiteral(value, poisonMask, bitCount,hasSpecifiedBitCount).asInstanceOf[this.type]
  override def opName: String = "U\"xxx\""
  override def toString = "(U" + super.toString
}

class SIntLiteral extends BitVectorLiteral{
  override def getTypeObject = TypeSInt
  override def isSignedKind: Boolean = true
  override def clone(): this.type = SIntLiteral(value, poisonMask, bitCount,hasSpecifiedBitCount).asInstanceOf[this.type]
  override def opName: String = "S\"xxx\""
  override def toString = "(S" + super.toString
}

//class BitsAllToLiteral(val theConsumer : Node,val value: Boolean) extends Literal with Widthable {
//  override def calcWidth: Int = theConsumer.asInstanceOf[WidthProvider].getWidth
//  override def getBitsStringOn(bitCount: Int): String = (if(value) "1" else "0" ) * bitCount
//  override def getValue(): BigInt = if(value) (BigInt(1) << getWidth) - 1 else 0
//}
//
//
//
object BoolLiteral {
  def apply(value: Boolean, on: Bool): Bool = {
    on.assignFrom(new BoolLiteral(value))
    on
  }
}

class BoolLiteral(val value: Boolean) extends Literal {
  override def getTypeObject = TypeBool
  override def opName: String = "Bool(x)"


  override def normalizeInputs: Unit = {}

  override def clone(): this.type = new BoolLiteral(value).asInstanceOf[this.type]

  override def getValue(): BigInt = if(value) 1 else 0
  override def getBitsStringOn(bitCount: Int, poisonSymbol : Char): String = {
    assert(bitCount == 1)
    (if(value) "1" else "0")
  }
  override def hasPoison() = false
}

class BoolPoison() extends Literal {
  override def getValue(): BigInt = throw new Exception("Poison have no values")
  override def getTypeObject = TypeBool
  override def opName: String = "Bool(?)"
  override def hasPoison() = true
  override def normalizeInputs: Unit = {}

  override def clone(): this.type = new BoolPoison().asInstanceOf[this.type]

  override def getBitsStringOn(bitCount: Int, poisonSymbol : Char): String = {
    assert(bitCount == 1)
    poisonSymbol.toString()
  }
}

//
//
//
//
////class STime(value : Double){
////  def decompose: (Double,String) = {
////    if(value > 3600.0) return (value/3600.0,"hr")
////    if(value > 60.0) return (value/60.0,"min")
////    if(value > 1.0) return (value/1.0,"sec")
////    if(value > 1.0e-3) return (value/1.0e-3,"ms")
////    if(value > 1.0e-6) return (value/1.0e-6,"us")
////    if(value > 1.0e-9) return (value/1.0e-9,"ns")
////    if(value > 1.0e-12) return (value/1.0e-12,"ps")
////    (value/1.0e-15,"fs")
////  }
////}
//