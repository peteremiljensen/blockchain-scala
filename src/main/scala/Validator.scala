package dk.diku.freechain

case class Validator(loaf: Loaf => Boolean, block: Block => Boolean,
  branching: (List[Block], List[Block]) => List[Block])
