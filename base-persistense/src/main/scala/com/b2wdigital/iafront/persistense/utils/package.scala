package com.b2wdigital.iafront.persistense

package object utils {
  def whiley[T](cond : =>Boolean)(body : =>T) : T = {
    @scala.annotation.tailrec
    def loop(previous : T) : T = if(cond) loop(body) else previous
    if(cond) loop(body) else throw new Exception("Loop must be entered at least once.")
  }
}
