package com.puzzlenook.sudoku

import kotlin.random.Random

/** Shared pieces for the Latin-square puzzles (Futoshiki, Calcudoku, Skyscrapers). */
object LatinGenerator {
    /** Grid size for each puzzle and difficulty. */
    fun size(variant: Variant, d: Difficulty): Int = when (variant) {
        Variant.FUTOSHIKI -> intArrayOf(5, 6, 7, 8)[d.ordinal]
        Variant.CALCUDOKU -> intArrayOf(4, 6, 7, 8)[d.ordinal]
        Variant.SKYSCRAPERS -> intArrayOf(4, 5, 6, 7)[d.ordinal]
        Variant.KROPKI -> intArrayOf(5, 6, 7, 8)[d.ordinal]
        else -> 9
    }

    /** Share of the removable clues put back to make easier levels gentler. */
    fun extraClues(d: Difficulty): Double = doubleArrayOf(0.3, 0.15, 0.05, 0.0)[d.ordinal]

    fun generate(variant: Variant, d: Difficulty, random: Random = Random.Default): Puzzle = when (variant) {
        Variant.FUTOSHIKI -> FutoshikiGenerator.generate(d, random)
        Variant.CALCUDOKU -> CalcudokuGenerator.generate(d, random)
        Variant.SKYSCRAPERS -> SkyscrapersGenerator.generate(d, random)
        Variant.KROPKI -> KropkiGenerator.generate(d, random)
        else -> error("$variant is not a Latin-square puzzle")
    }

    /** A random filled n×n Latin square. */
    fun randomSquare(n: Int, random: Random): IntArray {
        val board = IntArray(n * n)
        val rows = IntArray(n)
        val cols = IntArray(n)
        fun fill(i: Int): Boolean {
            if (i == n * n) return true
            val r = i / n
            val c = i % n
            for (v in (1..n).shuffled(random)) {
                val bit = 1 shl v
                if (rows[r] and bit != 0 || cols[c] and bit != 0) continue
                rows[r] = rows[r] or bit
                cols[c] = cols[c] or bit
                board[i] = v
                if (fill(i + 1)) return true
                rows[r] = rows[r] xor bit
                cols[c] = cols[c] xor bit
            }
            board[i] = 0
            return false
        }
        fill(0)
        return board
    }

    /** Solvable step by step without guessing (which also means exactly one solution). */
    fun solvable(rules: LatinRules, givens: IntArray) = LatinLogic.solves(rules, givens)

    internal fun deadline(ms: Long) = System.nanoTime() + ms * 1_000_000
    internal fun past(deadline: Long) = System.nanoTime() > deadline
}

object FutoshikiGenerator {
    /**
     * Starts from every inequality between neighbours and every digit, removes digits and
     * then signs while it can still be solved without guessing, and puts some signs back on easier levels.
     */
    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 2500): Puzzle {
        val n = LatinGenerator.size(Variant.FUTOSHIKI, d)
        val deadline = LatinGenerator.deadline(budgetMs)
        val solution = LatinGenerator.randomSquare(n, random)
        val all = buildList {
            for (r in 0 until n) for (c in 0 until n) {
                val i = r * n + c
                if (c + 1 < n) add(i to i + 1)
                if (r + 1 < n) add(i to i + n)
            }
        }.map { (a, b) -> if (solution[a] < solution[b]) intArrayOf(a, b) else intArrayOf(b, a) }
        val signs = all.toMutableList()
        val givens = solution.copyOf()
        var rules = LatinRules(n, signs.toList())
        for (i in solution.indices.shuffled(random)) {
            if (LatinGenerator.past(deadline)) break
            givens[i] = 0
            if (!LatinGenerator.solvable(rules, givens)) givens[i] = solution[i]
        }
        val removed = mutableListOf<IntArray>()
        for (s in signs.shuffled(random)) {
            if (LatinGenerator.past(deadline)) break
            signs.remove(s)
            val test = rules.withLess(signs.toList())
            if (LatinGenerator.solvable(test, givens)) {
                rules = test
                removed += s
            } else {
                signs += s
            }
        }
        val extra = (removed.size * LatinGenerator.extraClues(d)).toInt()
        val finalSigns = signs + removed.shuffled(random).take(extra)
        return Puzzle(givens, solution, d, size = n, rules = LatinRules(n, finalSigns))
    }
}

object KropkiGenerator {
    /**
     * Every dot of the random solution is shown (so a missing dot means neither), then digits are
     * removed while it can still be solved without guessing, and some are put back on easier levels.
     */
    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 2500): Puzzle {
        val n = LatinGenerator.size(Variant.KROPKI, d)
        val deadline = LatinGenerator.deadline(budgetMs)
        val solution = LatinGenerator.randomSquare(n, random)
        val white = mutableListOf<IntArray>()
        val black = mutableListOf<IntArray>()
        for (r in 0 until n) for (c in 0 until n) {
            val i = r * n + c
            for (j in listOfNotNull((i + 1).takeIf { c + 1 < n }, (i + n).takeIf { r + 1 < n })) {
                when (LatinRules.dotKind(solution[i], solution[j])) {
                    1 -> white += intArrayOf(i, j)
                    2 -> black += intArrayOf(i, j)
                }
            }
        }
        val rules = LatinRules(n, white = white, black = black, kropkiAll = true)
        val givens = solution.copyOf()
        val removed = mutableListOf<Int>()
        for (i in solution.indices.shuffled(random)) {
            if (LatinGenerator.past(deadline)) break
            givens[i] = 0
            if (LatinGenerator.solvable(rules, givens)) removed += i else givens[i] = solution[i]
        }
        // Easier levels get some digits back so the grid isn't nearly bare.
        for (i in removed.shuffled(random).take((removed.size * LatinGenerator.extraClues(d)).toInt())) givens[i] = solution[i]
        return Puzzle(givens, solution, d, size = n, rules = rules)
    }
}

object CalcudokuGenerator {
    fun maxCage(d: Difficulty) = intArrayOf(2, 3, 4, 4)[d.ordinal]

    /**
     * Cuts the grid into random cages with random operations. While the puzzle can't be
     * solved without guessing, a cage where the logic gets stuck gets a new operation or is split up.
     */
    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 2500): Puzzle {
        val n = LatinGenerator.size(Variant.CALCUDOKU, d)
        val deadline = LatinGenerator.deadline(budgetMs)
        var best: Puzzle? = null
        var bestSingles = Int.MAX_VALUE
        repeat(4) { attempt ->
            if (attempt > 0 && LatinGenerator.past(deadline)) return@repeat
            val solution = LatinGenerator.randomSquare(n, random)
            val groups = partition(n, maxCage(d), random)
            var cages = groups.map { makeCage(it, solution, random) }
            val empty = IntArray(n * n)
            val rerolls = mutableMapOf<List<Int>, Int>()
            while (true) {
                val logic = LatinLogic.run(LatinRules(n, cages = cages), empty)
                if (logic.solved) break
                // Work on a cage where the logic got stuck.
                val target = logic.unresolved.filter { i -> cages.first { i in it.cells }.cells.size > 1 }.randomOrNull(random) ?: break
                val cage = cages.first { target in it.cells }
                // First try the same cage with another operation; split it only if that keeps failing.
                val tries = rerolls.getOrDefault(cage.cells, 0)
                if (tries < 3) {
                    rerolls[cage.cells] = tries + 1
                    val other = makeCage(cage.cells, solution, random)
                    if (other != cage) {
                        cages = cages - cage + other
                        continue
                    }
                }
                val parts = components(cage.cells - target, n) + listOf(listOf(target))
                cages = cages - cage + parts.map { makeCage(it, solution, random) }
            }
            val singles = cages.count { it.cells.size == 1 }
            if (singles < bestSingles) {
                bestSingles = singles
                best = Puzzle(IntArray(n * n), solution, d, size = n, rules = LatinRules(n, cages = cages))
            }
            if (singles <= 1) return best!!
        }
        return best!!
    }

    private fun partition(n: Int, maxSize: Int, random: Random): List<List<Int>> {
        val owner = IntArray(n * n) { -1 }
        val groups = mutableListOf<MutableList<Int>>()
        for (start in (0 until n * n).shuffled(random)) {
            if (owner[start] >= 0) continue
            val g = mutableListOf(start)
            owner[start] = groups.size
            // Mostly 2–3 cells; singles are rare.
            val want = if (random.nextFloat() < 0.06f) 1 else 2 + random.nextInt(maxSize - 1)
            while (g.size < want) {
                val next = g.flatMap { neighbours(it, n) }.filter { owner[it] < 0 }.randomOrNull(random) ?: break
                owner[next] = groups.size
                g += next
            }
            groups += g
        }
        // Merge leftover singles into a neighbouring small cage where possible.
        for (g in groups.filter { it.size == 1 }) {
            // A single may already have taken in another single; then it's no longer one.
            if (g.size != 1 || random.nextFloat() < 0.15f) continue
            val target = neighbours(g[0], n).map { owner[it] }.distinct()
                .filter { groups[it].size in 1 until maxSize && groups[it] !== g }.randomOrNull(random) ?: continue
            groups[target] += g[0]
            owner[g[0]] = target
            g.clear()
        }
        return groups.filter { it.isNotEmpty() }
    }

    private fun neighbours(i: Int, n: Int) = buildList {
        val r = i / n
        val c = i % n
        if (r > 0) add(i - n)
        if (r < n - 1) add(i + n)
        if (c > 0) add(i - 1)
        if (c < n - 1) add(i + 1)
    }

    private fun components(cells: List<Int>, n: Int): List<List<Int>> {
        val left = cells.toMutableSet()
        val out = mutableListOf<List<Int>>()
        while (left.isNotEmpty()) {
            val start = left.first()
            val comp = mutableListOf(start)
            left -= start
            var k = 0
            while (k < comp.size) {
                for (m in neighbours(comp[k++], n)) if (m in left) {
                    left -= m
                    comp += m
                }
            }
            out += comp
        }
        return out
    }

    private fun makeCage(cells: List<Int>, solution: IntArray, random: Random): MathCage {
        val values = cells.map { solution[it] }
        val op = when (cells.size) {
            1 -> '='
            2 -> {
                val divisible = maxOf(values[0], values[1]) % minOf(values[0], values[1]) == 0
                when {
                    divisible && random.nextFloat() < 0.5f -> '÷'
                    random.nextFloat() < 0.5f -> '-'
                    random.nextBoolean() -> '+'
                    else -> '×'
                }
            }
            else -> if (random.nextFloat() < 0.55f) '+' else '×'
        }
        return MathCage(op, LatinRules.cageValue(op, values)!!, cells.sorted())
    }
}

object SkyscrapersGenerator {
    /**
     * Starts from all 4n edge clues (adding digits where logic gets stuck), then
     * removes digits and clues while it can still be solved without guessing.
     */
    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 2500): Puzzle {
        val n = LatinGenerator.size(Variant.SKYSCRAPERS, d)
        val deadline = LatinGenerator.deadline(budgetMs)
        val solution = LatinGenerator.randomSquare(n, random)
        val base = LatinRules(n)
        val sky = IntArray(4 * n) { LatinRules.visible(solution, base.lines[it]) }
        val givens = IntArray(n * n)
        var rules = base.withSky(sky.copyOf())
        while (true) {
            val logic = LatinLogic.run(rules, givens)
            if (logic.solved) break
            // If the logic stopped with nothing left open, fall back to any cell not yet given.
            val open = logic.unresolved.filter { givens[it] == 0 }.ifEmpty { givens.indices.filter { givens[it] == 0 } }
            val i = open.randomOrNull(random) ?: break
            givens[i] = solution[i]
        }
        // Digits first (all clues still help), then clues.
        for (i in givens.indices.filter { givens[it] != 0 }.shuffled(random)) {
            givens[i] = 0
            if (!LatinGenerator.solvable(rules, givens)) givens[i] = solution[i]
        }
        val clues = rules.sky!!.copyOf()
        val removed = mutableListOf<Int>()
        for (k in clues.indices.shuffled(random)) {
            if (LatinGenerator.past(deadline)) break
            val v = clues[k]
            clues[k] = 0
            val test = base.withSky(clues.copyOf())
            if (LatinGenerator.solvable(test, givens)) {
                rules = test
                removed += k
            } else {
                clues[k] = v
            }
        }
        val extra = (removed.size * LatinGenerator.extraClues(d)).toInt()
        for (k in removed.shuffled(random).take(extra)) clues[k] = sky[k]
        return Puzzle(givens, solution, d, size = n, rules = base.withSky(clues))
    }
}
