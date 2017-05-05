package dk.diku.blockchain

case class Validator(loaf: Loaf => Boolean, block: Block => Boolean,
  consensusCheck: (Integer, Integer) => Boolean,
  consensus: (List[Block], List[Block]) => List[Block])
