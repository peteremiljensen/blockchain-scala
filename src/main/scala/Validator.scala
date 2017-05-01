package dk.diku.blockchain

case class Validator(loaf: Loaf => Boolean, block: Block => Boolean)
