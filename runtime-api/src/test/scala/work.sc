import scala.annotation.tailrec

def countChange(money: Int, coins: List[Int]): Int = {
  var sols: Int = 0;
  def checkCoins(partialSolution: List[Int], coinsLeft: List[Int]): Int = {
    coinsLeft.foldRight(coinsLeft)((coin: Int, coinsLeft) => {
      val newSolution: List[Int] = coin :: partialSolution
      println(newSolution)
      val moneyFound: Int = newSolution.sum
      if (moneyFound == money) {
        //println("Solution Found!")
        sols = sols + 1
      }
      else if (moneyFound < money) {
        checkCoins(newSolution, coinsLeft)
      }
      coinsLeft.init
    })
    sols
  }
  checkCoins(List(), coins)
}

def countChange2(money: Int, coins: List[Int]): Int = {
  //var sols: Int = 0;
  def checkCoins(partialSolution: List[Int], coinsLeft: List[Int]): Int = {
    coinsLeft.foldRight((coinsLeft, 0))((coin: Int, tup: (List[Int], Int)) =>
      tup match {
        case (coinsLeft, sols) =>
          val newSolution: List[Int] = coin :: partialSolution
          println(newSolution)
          val moneyFound: Int = newSolution.sum
          if (moneyFound == money) {
            //println("Solution Found!")
            (coinsLeft.init, sols + 1)
          }
          else if (moneyFound < money) {
            (coinsLeft.init, checkCoins(newSolution, coinsLeft))
          }
          else
            (coinsLeft.init, 0)
      })._2
  }
  checkCoins(List(), coins)
}

def countChange3(money: Int, coins: List[Int]): Int = {
  def count(money: Int, coins: List[Int], nsol: Int): Int = {
    if (money < 0) nsol
    else if (money == 0) nsol + 1
    else explore(money, coins, nsol)
  }
  @tailrec def explore(money: Int, coins: List[Int], nsol: Int): Int = coins match {
    case Nil => nsol
    case coin :: coinsLeft => explore(money, coinsLeft, count(money - coin, coins, nsol))
  }
  count(money, coins, 0)
}

val n1 = countChange(4, List(1, 2))
val n2 = countChange2(4, List(1, 2))
val n3 = countChange3(4, List(1, 2, 3))

n1
n2
n3
