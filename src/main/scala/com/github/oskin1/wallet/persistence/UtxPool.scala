package com.github.oskin1.wallet.persistence

import com.github.oskin1.wallet.{persistence, ModifierId}

import scala.collection.immutable.TreeSet

/** In-memory persistence for unconfirmed transactions.
  *
  * @param txs       - pool of transaction ids zipped with a related chat ids.
  * @param heightOpt - blockchain height against which current pool state is valid.
  *                    None if current pool state validity hasn't yet been checked
  *                    against blockchain.
  */
final case class UtxPool(
  txs: TreeSet[(ModifierId, Long)],
  heightOpt: Option[Int]
) {

  /** Add an element to the pool
    */
  def add(elem: (ModifierId, Long)): UtxPool = this.copy(txs = txs + elem)

  /** Eliminate elements satisfying a given predicate `p` from the pool.
    */
  def filterNot(p: ModifierId => Boolean): UtxPool =
    this.copy(txs = txs.filterNot(x => p(x._1)))

  /** Update height with a given one.
    */
  def setHeight(height: Int): UtxPool = this.copy(heightOpt = Some(height))
}

object UtxPool {

  def empty: UtxPool = persistence.UtxPool(TreeSet.empty, None)
}
