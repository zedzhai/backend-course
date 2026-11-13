fun main() {
    println("===== Lab2 Simple Calculator =====")
    while (true) {
        println("\nEnter first number:")
        val num1 = readLine()?.toDoubleOrNull()
        if (num1 == null) {
            println("Invalid input, please enter a number!")
            continue
        }

        println("Enter operator (+ - * /):")
        val op = readLine()
        if (op !in listOf("+", "-", "*", "/")) {
            println("Invalid operator! Only + - * / supported.")
            continue
        }

        println("Enter second number:")
        val num2 = readLine()?.toDoubleOrNull()
        if (num2 == null) {
            println("Invalid input, please enter a number!")
            continue
        }

        val result = when(op) {
            "+" -> num1 + num2
            "-" -> num1 - num2
            "*" -> num1 * num2
            "/" -> {
                if (num2 == 0.0) {
                    println("Error: division by zero!")
                    null
                } else {
                    num1 / num2
                }
            }
            else -> null
        }

        if (result != null) {
            println("Result: $num1 $op $num2 = $result")
        }

        println("Continue? y/n")
        val again = readLine()
        if (again != "y") {
            println("Program exit.")
            break
        }
    }
}
