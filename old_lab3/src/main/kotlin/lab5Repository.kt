/**
 * Lab5 - Book In-Memory Repository
 */
class lab5Repository {
    private val storage = mutableListOf<Book>()
    private var nextId = 1

    fun create(title: String, author: String, year: Int): Book {
        val book = Book(nextId++, title, author, year)
        storage.add(book)
        return book
    }

    fun findAll(): List<Book> = storage.toList()

    fun findById(id: Int): Book? = storage.find { it.id == id }

    fun update(id: Int, title: String, author: String, year: Int): Book? {
        val old = findById(id) ?: return null
        storage.remove(old)
        val newBook = old.copy(title = title, author = author, year = year)
        storage.add(newBook)
        return newBook
    }

    fun delete(id: Int): Boolean {
        val b = findById(id) ?: return false
        storage.remove(b)
        return true
    }
}
