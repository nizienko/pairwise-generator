package org.jetbrains.test.pairwise

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import java.util.*

class PairwiseGeneratorTest {

    @Test
    fun generateCases() {
        val generator = PairwiseGeneratorImpl(Paths.get("src/test/resources/TestData.md").toFile())
        generator.generateCases()
    }

    @Test
    fun generateTestData() {
        val output = Paths.get("src/test/resources/TestData3.md").toFile()
        output.mkdirs()
        output.writeText("### Load test\n")
        (1..100).forEach { parameter->
            output.appendText("Parameter $parameter: ")
            val options = StringJoiner(", ")
            (1..1 + parameter % 9).forEach { value ->
                options.add("value $value")
            }
            output.appendText(options.toString() + "\n")
        }
    }
}