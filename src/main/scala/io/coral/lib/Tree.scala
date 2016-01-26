/*
 * Copyright 2016 Coral realtime streaming analytics (http://coral-streaming.github.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.coral.lib

import scala.collection.mutable.{ArrayBuffer, Stack}

case class Node[K](value: K, var left: Option[Node[K]], var right: Option[Node[K]], var parent: Option[Node[K]]) {
	def hasLeft: Boolean = if (left != None) true else false
	def hasRight: Boolean = if (right != None) true else false
	def hasParent: Boolean = if (parent != None) true else false
	def isLeaf: Boolean = !hasLeft && !hasRight

	def isParent(n: Node[K]): Boolean = {
		if (!isLeaf) {
			val l = if (hasLeft) left.get else null
			val r = if (hasRight) right.get else null
			l == n || r == n
		}

		else false
	}
}

abstract class BinaryTree[K] {
	def add(value: K)
	def remove(value: K): Boolean
	def height: Int
	def size: Int
}

class Tree[K](implicit ord: K => Ordered[K]) extends BinaryTree[K] {
	var root: Option[Node[K]] = None
	private var count = 0

	override def add(value: K) {
		root match {
			case None => root = Some(new Node[K](value, None, None, None)); count = 1
			case Some(node) => if (insert(node, value)) count += 1
		}
	}

	def insert(node: Node[K], newVal: K): Boolean = {
		if (newVal <= node.value) {
			node match {
				case Node(_, None, _, _) => node.left =
					Some(new Node[K](newVal, None, None, Some(node)))
					true
				case Node(_, Some(left), _, _) => insert(left, newVal)
			}
		} else if (newVal > node.value) {
			node match {
				case Node(_, _, None, _) => node.right =
					Some(new Node[K](newVal, None, None, Some(node)))
					true
				case Node(_, _, Some(right), _) => insert(right, newVal)
			}
		} else false //this removes the duplicate values from tree
	}

	override def remove(value: K): Boolean = {
		root match {
			case None => false
			case Some(node) => {
				binarySearch(value, node) match {
					case None => false
					case Some(node) => {
						count -= 1
						delete(node)
						true
					}
				}
			}
		}
	}

	def delete(node: Node[K]) {
		node match {
			case Node(value, None, None, Some(parent)) => updateParent(parent, value, None)
			case Node(value, Some(child), None, Some(parent)) => {
				updateParent(parent, value, Some(child))
				child.parent = Some(parent)
			}
			case Node(value, None, Some(child), Some(parent)) => {
				updateParent(parent, value, Some(child))
				child.parent = Some(parent)
			}
			case Node(_, Some(child), None, None) => {
				root = Some(child)
				child.parent = None
			}
			case Node(_, None, Some(child), None) => {
				root = Some(child)
				child.parent = None
			}
			case Node(_, Some(left), Some(right), _) => {
				var child = right
				while (child.left != None) {
					child = child.left.get
				}
				node.parent match {
					case Some(parent) => updateParent(parent, node.value, Some(child))
					case None => root = Some(child)
				}
				child.left = node.left
				child.right = node.right
				left.parent = Some(child)
				right.parent = Some(child)
				if (child.hasParent && child.parent.get.hasLeft && child.parent.get.left.get == child)
					child.parent.get.left = None
				else child.parent.get.right = None
				child.parent = node.parent
			}
			case _ =>
		}

		def updateParent(parent: Node[K], value: K, newChild: Option[Node[K]]) {
			if (value < parent.value) parent.left = newChild
			else parent.right = newChild
		}
	}

	def binarySearch(value: K, node: Node[K]): Option[Node[K]] = {
		if (value == node.value)
			Some(node)
		else if (value <= node.value) {
			node match {
				case Node(_, None, _, _) => None
				case Node(_, Some(left), _, _) => binarySearch(value, left)
			}
		} else {
			node match {
				case Node(_, _, None, _) => None
				case Node(_, _, Some(right), _) => binarySearch(value, right)
			}
		}
	}

	def inorder: Seq[K] = {
		val nodes = new ArrayBuffer[K]()
		val stack = new Stack[Node[K]]()
		if (size != 0) {
			var cur = root
			while (!stack.isEmpty || cur != None) {
				cur match {
					case Some(node) => {
						stack.push(node)
						cur = node.left
					}
					case None => {
						val tmp = stack.pop()
						nodes += tmp.value
						cur = tmp.right
					}
				}
			}
		}
		nodes
	}

	def preorder: Seq[K] = {
		val nodes = new ArrayBuffer[K]()
		val stack = new Stack[Node[K]]()
		if (size != 0) {
			var cur = root
			while (!stack.isEmpty || cur != None) {
				cur match {
					case Some(node) => {
						stack.push(node)
						nodes += node.value
						cur = node.left
					}
					case None => {
						val tmp = stack.pop()
						cur = tmp.right
					}
				}
			}
		}
		nodes
	}

	def postorder: Seq[K] = {
		val nodes = new ArrayBuffer[K]()
		val stack = new Stack[Node[K]]()
		if (size != 0) {
			var prev: Option[Node[K]] = None
			stack.push(root.get)
			while (!stack.isEmpty) {
				val cur = stack.top
				prev match {
					case None => if (cur.hasLeft) stack.push(cur.left.get) else if (cur.hasRight) stack.push(cur.right.get)
					case Some(node) => {
						if (!cur.isParent(node) && cur.hasLeft) stack.push(cur.left.get)
						else if (!cur.isParent(node) && cur.hasRight) stack.push(cur.right.get)
						else if (cur.isParent(node) && cur.hasRight && cur.hasLeft && cur.left.get == node) stack.push(cur.right.get)
						else {
							stack.pop()
							nodes += cur.value
						}
					}
				}
				prev = Some(cur)
			}
		}
		nodes
	}

	override def toString: String = {
		postorder.mkString(" : ")
	}

	override def height: Int = depth(root) - 1

	def depth(node: Option[Node[K]]): Int = {
		node match {
			case None => 0
			case Some(n) => 1 + scala.math.max(depth(n.left), depth(n.right))
		}
	}

	def size = count
}
