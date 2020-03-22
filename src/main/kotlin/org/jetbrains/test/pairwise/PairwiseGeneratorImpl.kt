package org.jetbrains.test.pairwise

import java.io.File
import java.lang.IllegalArgumentException
import java.util.*

class PairwiseGeneratorImpl(private val file: File) : PairwiseGenerator {

    private fun collectAllPairs(): Set<ParameterPair> {
        if (file.exists().not()) {
            throw IllegalArgumentException("${file.absolutePath} is not exists")
        }

        val lines = file.readLines()

        val parameters = lines.mapNotNull { Parameter.createFromLine(it) }.sortedByDescending { it.options.size }
        parameters.forEach { println(it) }
        val doneParameterNames = mutableListOf<String>()
        val pairs = mutableSetOf<ParameterPair>()
        parameters.forEach { firstParameter ->
            doneParameterNames.add(firstParameter.name)
            parameters.filter { doneParameterNames.contains(it.name).not() }.forEach { secondParameter ->
                firstParameter.options.forEach { firstParameterOption ->
                    secondParameter.options.forEach { secondParameterOption ->
                        // filter rules
                        pairs.add(
                            ParameterPair(
                                ParameterValue(firstParameter.name, firstParameter.options.size, firstParameterOption),
                                ParameterValue(
                                    secondParameter.name,
                                    secondParameter.options.size,
                                    secondParameterOption
                                )
                            )
                        )
                    }
                }
            }
        }
        return pairs
    }

    private fun quickDistribution(testCases: MutableList<WorkInProgressTestCase>, pairs: MutableSet<ParameterPair>) {
        val pair = pairs.first()
        println("---------------------------------\nprocessing '$pair'")
        pairs.remove(pair)

        val fittedTestCases = testCases.filter { it.isFit(pair) }
        println("'$pair' can be added to following:\n$fittedTestCases")
        if (fittedTestCases.isEmpty()) {
            val newTestCase = WorkInProgressTestCase()
            newTestCase.addPair(pair)
            testCases.add(newTestCase)
            println("Created new test case for '$pair'")
        } else {
            val chosenTestCase = fittedTestCases.maxBy {
                it.predictNewPairs(pair).count { newPair -> pairs.contains(newPair) }
            }!!
            val newPairs = chosenTestCase.predictNewPairs(pair)
            println("chosen test case: $chosenTestCase")
            println("it also cover $newPairs(${newPairs.count { pairs.contains(it) }})")
            chosenTestCase.addPair(pair)
            pairs.removeAll(newPairs)
        }
        println("${pairs.size} pairs left")
    }

    private fun slowDistribution(testCases: MutableList<WorkInProgressTestCase>, pairs: MutableSet<ParameterPair>) {
        var bestTestCase: WorkInProgressTestCase? = null
        var bestPair: ParameterPair? = null
        var bestPoint = -1
        var bestRepeat = 0
        pairs.forEach { pair ->
            testCases.filter { it.isFit(pair) }.forEach { testCase ->
                val newPairs = testCase.predictNewPairs(pair)
                val points = newPairs.count { pairs.contains(it) }

                val repeats = newPairs.size - points

                if (bestPoint < points) {
                    bestPoint = points
                    bestTestCase = testCase
                    bestPair = pair
                    bestRepeat = repeats
                } else if (bestPoint == points) {
                    if (bestRepeat > repeats) {
                        bestPoint = points
                        bestTestCase = testCase
                        bestPair = pair
                        bestRepeat = repeats
                    }
                }
            }
        }
        if (bestTestCase == null && bestPair == null) {
            val pair = pairs.maxBy { it.weight }!!
            val newTestCase = WorkInProgressTestCase()
            newTestCase.addPair(pair)
            testCases.add(newTestCase)
            pairs.remove(pair)
            println("Created new test case for '$pair'")
        } else {
            println("Best option || $bestTestCase ||<--- '$bestPair'  -  $bestPoint")
            val newPairs = bestTestCase!!.predictNewPairs(bestPair!!)
            println("chosen test case: $bestTestCase")
            println("it also cover $newPairs($bestPoint)")
            bestTestCase!!.addPair(bestPair!!)
            pairs.remove(bestPair!!)
            pairs.removeAll(newPairs)
        }
    }

    override fun generateCases(): List<TestCase> {

        val pairsToProcess = collectAllPairs().shuffled().toMutableSet()

        println("total pairs: " + pairsToProcess.size)
        val testCases = mutableListOf<WorkInProgressTestCase>()

        while (pairsToProcess.isNotEmpty()) {
            println("\n=========================")
            if (pairsToProcess.size < 1000) {
                slowDistribution(testCases, pairsToProcess)
            } else {
                quickDistribution(testCases, pairsToProcess)
            }
        }
        testCases.forEach {
            println(it)
        }

        println("Test cases: ${testCases.size}")
        return emptyList()
    }
}

class WorkInProgressTestCase {
    private val parameters = mutableSetOf<ParameterValue>()

    fun isFit(pair: ParameterPair): Boolean {
        return (parameters.contains(pair.first) && parameters.none { it.name == pair.second.name })
                || (parameters.contains(pair.second) && parameters.none { it.name == pair.first.name })
                || (parameters.none { it.name == pair.first.name || it.name == pair.second.name })
    }

    fun predictNewPairs(pair: ParameterPair): List<ParameterPair> {
        val newPairs = mutableListOf<ParameterPair>()
        if (parameters.none { it.name == pair.first.name }) {
            parameters.forEach { newPairs.add(ParameterPair(it, pair.first)) }
        }
        if (parameters.none { it.name == pair.second.name }) {
            parameters.forEach { newPairs.add(ParameterPair(it, pair.second)) }
        }
        return newPairs
    }

    val size
        get() = parameters.size

    fun addPair(pair: ParameterPair) {
        parameters.add(pair.first)
        parameters.add(pair.second)
    }

    override fun toString(): String {
        return parameters.sortedBy { it.name }
            .let { p -> StringJoiner("; ").apply { p.forEach { add(it.toString()) } }.toString() }
    }
}

data class ParameterValue(val name: String, val weight: Int, val value: String) {
    override fun toString(): String {
        return "$name = $value"
    }
}

class ParameterPair(val first: ParameterValue, val second: ParameterValue) {
    override fun equals(other: Any?): Boolean {
        if (other is ParameterPair) {
            return (first == other.first && second == other.second)
                    || (first == other.second && second == other.first)
        }
        return false
    }

    val weight = first.weight + second.weight

    override fun hashCode(): Int {
        return first.hashCode() + second.hashCode()
    }

    override fun toString(): String {
        return "${first.name}: ${first.value} >--< ${second.name}: ${second.value}"
    }
}

data class Parameter(val name: String, val options: List<String>) {
    companion object {
        fun createFromLine(line: String): Parameter? {
            if (line.contains(":").not()) {
                return null
            }
            val name = line.substringBefore(":").trim()
            val options = line.substringAfter(":").split(",").map { it.trim() }
            return Parameter(name, options)
        }
    }
}