/**
 * Lab5 - Book Business Service (validation)
 */
class lab5Service(private val repo: lab5Repository) {

    fun createBook(title: String, author: String, year: Int): BookResult {
        val errors = mutableListOf<String>()
        if (title.isBlank()) errors.add("title cannot be blank")
        if (author.isBlank()) errors.add("author cannot be blank")
        if (year < 1000 || year > 2026) errors.add("year is invalid")

        if (errors.isNotEmpty()) return BookResult.ValidationError(errors)
        val book = repo.create(title, author, year)
        return BookResult.Success(book)
    }

    fun getAll(): BookResult.SuccessList {
        return BookResult.SuccessList(repo.findAll())
    }

    fun getById(id: Int): BookResult {
        val b = repo.findById(id) ?: return BookResult.NotFound("Book $id not found")
        return BookResult.Success(b)
    }

    fun updateBook(id: Int, title: String, author: String, year: Int): BookResult {
        val errors = mutableListOf<String>()
        if (title.isBlank()) errors.add("title cannot be blank")
        if (author.isBlank()) errors.add("author cannot be blank")
        if (year < 1000 || year > 2026) errors.add("year is invalid")
        if (errors.isNotEmpty()) return BookResult.ValidationError(errors)

        val updated = repo.update(id, title, author, year)
            ?: return BookResult.NotFound("Book $id not found")
        return BookResult.Success(updated)
    }

    fun deleteBook(id: Int): BookResult {
        val ok = repo.delete(id)
        return if (ok) BookResult.Success(Book(id,"","",0))
        else BookResult.NotFound("Book $id not found")
    }
}
