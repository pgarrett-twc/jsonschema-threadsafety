import io.github.optimumcode.json.schema.JsonSchemaLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertTrue

class ThreadSafetyTests {
    companion object {
        private fun resourceText(name: String): String =
            this::class.java.classLoader
                .getResourceAsStream(name)!!
                .bufferedReader()
                .use { it.readText() }

        private val String.asJson
            get() =
                Json.parseToJsonElement(this).jsonObject
    }

    private val schemaLoader = JsonSchemaLoader.create().apply {
        register(resourceText("fstab.schema.json").asJson)
        register(resourceText("fstab-entry.schema.json").asJson)
    }

    private val key = "\$"
    private val schema = schemaLoader.fromDefinition("""{"${key}ref": "https://example.com/fstab"}""")
    private val doc = resourceText("fstab.json").asJson

    @Test
    fun `safe to reuse schema`() {
        repeat(2) {
            val isValid = schema.validate(doc) {}
            assertTrue(isValid)
        }
    }

    // This test usually fails with one of these two exceptions:
    // 1. java.util.NoSuchElementException: ArrayDeque is empty.
    // 2. java.lang.ArrayIndexOutOfBoundsException: arraycopy: ...
    @Test
    fun `safe to use schema from simultaneous coroutines`(): Unit = runBlocking(Dispatchers.Default) {
        repeat(100) {
            launch {
                repeat(10) {
                    val isValid = schema.validate(doc) {}
                    assertTrue(isValid)
                }
            }
        }
    }
}
