package org.jetbrains.test.pairwise

interface PairwiseGenerator {
    fun generateCases(): List<TestCase>
}