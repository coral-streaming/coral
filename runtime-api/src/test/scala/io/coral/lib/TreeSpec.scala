package io.coral.lib

import io.coral.lib.Tree
import org.scalatest._

import scala.collection.mutable.ArrayBuffer

/**
 * Created by Hoda Alemi on 3/23/15.
 */
class TreeSpec extends WordSpecLike with ShouldMatchers {

  "A Tree" must{

    "add a value in the tree" in {
      val tree: Tree[Int] = new Tree[Int]()
      val emptyTree : Tree[Int] = new Tree[Int]()
      tree.add(5)
      tree should not equal(emptyTree)
    }

    "remove a value from the tree" in {
      val tree: Tree[Int] = new Tree[Int]()
      val anotherTree : Tree[Int] = new Tree[Int]()
      tree.add(1); tree.add(2); tree.add(3)
      anotherTree.add(1); anotherTree.add(2)
      tree.remove(3)
      tree.size should equal(anotherTree.size)
    }

    "have 0 height with one node" in {
      val tree: Tree[Int] = new Tree[Int]()
      tree.add(2)
      tree.height should equal(0)
    }

    "have correct height in an unbalance tree " in {
      val tree: Tree[Int] = new Tree[Int]()
      //tree.add(4); tree.add(3); tree.add(6); tree.add(2); tree.add(5); tree.add(1); tree.add(1)
      tree.add(1); tree.add(2); tree.add(3)
      tree.height should equal(2)
    }

    "have correct height in the balance tree" in {
      val tree: Tree[Int] = new Tree[Int]()
      tree.add(6); tree.add(3); tree.add(2); tree.add(4); tree.add(8); tree.add(8); tree.add(9)
      tree.height should equal(2)
    }

    "have size 0 for an empty tree" in {
      val tree: Tree[Int] = new Tree[Int]()
      tree.size should equal(0)
    }

    "have size 1 for tree with 1 node" in {
      val tree: Tree[Int] = new Tree[Int]()
      tree.add(10)
      tree.size should equal(1)
    }

    "have size 2 for a tree with 2 duplicate nodes" in{
      val tree: Tree[Int] = new Tree[Int]()
      tree.add(10); tree.add(10)
      tree.size should equal(2)
    }

    "traverse inorder(LNR)" in {
      val tree: Tree[Int] = new Tree[Int]()
      val expectedTraverse= new ArrayBuffer[Int]()
      expectedTraverse ++= ArrayBuffer(2, 3, 4, 6, 8, 8, 9)
      tree.add(6); tree.add(3); tree.add(2); tree.add(4); tree.add(8); tree.add(8); tree.add(9)
      tree.inorder should equal(expectedTraverse)
    }

    "traverse preorder(NLR)" in {
      val tree: Tree[Int] = new Tree[Int]()
      val expectedTraverse= new ArrayBuffer[Int]()
      expectedTraverse ++= ArrayBuffer(6, 3, 2, 4, 8, 8, 9)
      tree.add(6); tree.add(3); tree.add(2); tree.add(4); tree.add(8); tree.add(8); tree.add(9)
      tree.preorder should equal(expectedTraverse)
    }

    "traverse postorder(LRN)" in {
      val tree: Tree[Int] = new Tree[Int]()
      val expectedTraverse= new ArrayBuffer[Int]()
      expectedTraverse ++= ArrayBuffer(2, 4, 3, 8, 9, 8, 6)
      tree.add(6); tree.add(3); tree.add(2); tree.add(4); tree.add(8); tree.add(8); tree.add(9)
      tree.postorder should equal(expectedTraverse)
    }

    "" in {
      val tree: Tree[Int] = new Tree[Int]()
      tree.add(6); tree.add(3); tree.add(2); tree.add(4); tree.add(8); tree.add(8); tree.add(9)

    }
  }

}
