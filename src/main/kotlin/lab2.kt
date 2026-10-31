fun main() {
    println("===== Lab2 Simple Calculator =====")
    while (true) {
        println("\n请输入第一个数字：")
        val num1 = readLine()?.toDoubleOrNull()
        if (num1 == null) {
            println("输入无效，请输入数字！")
            continue
        }

        println("请输入运算符( + - * / )：")
        val op = readLine()
        if (op !in listOf("+", "-", "*", "/")) {
            println("运算符错误！只支持 + - * /")
            continue
        }

        println("请输入第二个数字：")
        val num2 = readLine()?.toDoubleOrNull()
        if (num2 == null) {
            println("输入无效，请输入数字！")
            continue
        }

        val result = when(op) {
            "+" -> num1 + num2
            "-" -> num1 - num2
            "*" -> num1 * num2
            "/" -> {
                if (num2 == 0.0) {
                    println("错误：不能除以0！")
                    null
                } else {
                    num1 / num2
                }
            }
            else -> null
        }

        if (result != null) {
            println("结果：$num1 $op $num2 = $result")
        }

        println("是否继续计算？y/n")
        val again = readLine()
        if (again != "y") {
            println("程序退出")
            break
        }
    }
}
