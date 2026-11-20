package com.puzzlenook.shade

import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.Variant
import kotlin.random.Random

/** `ShadePuzzle.numbers` of Tents: a tree (0 elsewhere). Trees are fixed and never hold a tent. */
const val TREE = 1

/**
 * Tents logic. Trees are fixed white; the other cells hold a tent ([SHADED]) or not ([WHITE]). Every
 * tree has exactly one tent next to it (one tent per tree, one tree per tent), tents never touch,
 * not even diagonally, and [counts] (rows 0..n-1, then columns; `-1` hidden) give the tents per line.
 *
 * Levels (0 = Easy ... 3 = Expert):
 * - 0: cells not next to a tree stay free, a tent frees its eight neighbours, a line that has its
 *   tents frees the rest, one whose open cells are all needed gets tents;
 * - 1: adds tree-tent matching (a tree may touch several tents, but they must pair off one to one):
 *   a tree with one possible tent cell gets it, as many tents as trees frees the rest, and every
 *   tree must keep a possible tent of its own (no matching, no puzzle);
 * - 2: as 1, plus what-if on single cells whose trial uses only the level 0 steps;
 * - 3: as 1, plus what-if on single cells whose trial uses the level 0 and 1 steps.
 */
class TentsLogic(private val n: Int, private val numbers: IntArray, private val counts: IntArray, private val level: Int) {
    private val cells = n * n
    private val isTree = BooleanArray(cells) { numbers[it] == TREE }
    private val trees = (0 until cells).filter { isTree[it] }
    private val treeNb: Map<Int, IntArray> = trees.associateWith { t -> orth(t, n).filter { !isTree[it] }.toIntArray() }
    private val adjTrees: Array<IntArray> = Array(cells) { i -> if (isTree[i]) IntArray(0) else orth(i, n).filter { isTree[it] }.toIntArray() }
    private val kingNb: Array<IntArray> = Array(cells) { king(it, n) }
    private val lines: Array<IntArray> = Array(2 * n) { k ->
        if (k < n) IntArray(n) { k * n + it } else IntArray(n) { it * n + (k - n) }
    }

    fun run(start: IntArray): ShadeResult {
        val s = start.copyOf()
        for (i in 0 until cells) if (isTree[i]) s[i] = WHITE
        val round = IntArray(cells) { if (s[it] != UNKNOWN) 0 else -1 }
        var r = 0
        while (true) {
            r++
            var res = propagate(s, level >= 1, false)
            if (res == NONE && level >= 2) res = whatIf(s)
            if (res == CONTRA) return ShadeResult(false, s, round)
            for (i in s.indices) if (round[i] < 0 && s[i] != UNKNOWN) round[i] = r
            if (s.none { it == UNKNOWN }) return ShadeResult(valid(s), s, round)
            if (res == NONE) return ShadeResult(false, s, round)
        }
    }

    private fun valid(s: IntArray): Boolean {
        for (i in 0 until cells) if (s[i] == SHADED) {
            if (adjTrees[i].isEmpty()) return false
            for (j in kingNb[i]) if (s[j] == SHADED) return false
        }
        for (k in 0 until 2 * n) if (counts[k] >= 0 && lines[k].count { s[it] == SHADED } != counts[k]) return false
        return matchSize(s) == trees.size && (0 until cells).count { s[it] == SHADED } == trees.size
    }

    /** Largest matching of trees to cells that are not [WHITE] (augmenting paths). */
    private fun matchSize(s: IntArray): Int {
        val owner = HashMap<Int, Int>()
        fun augment(t: Int, seen: HashSet<Int>): Boolean {
            for (c in treeNb.getValue(t)) {
                if (s[c] == WHITE || !seen.add(c)) continue
                val o = owner[c]
                if (o == null || augment(o, seen)) { owner[c] = t; return true }
            }
            return false
        }
        var size = 0
        for (t in trees) if (augment(t, HashSet())) size++
        return size
    }

    private fun propagate(s: IntArray, matching: Boolean, toFixpoint: Boolean): Int {
        var any = false
        while (true) {
            var res = basic(s)
            if (res == NONE && matching) res = match(s)
            if (res == CONTRA) return CONTRA
            if (res == NONE) return if (any) CHANGED else NONE
            any = true
            if (!toFixpoint) return CHANGED
        }
    }

    private fun basic(s: IntArray): Int {
        var changed = false
        for (i in 0 until cells) {
            if (s[i] == UNKNOWN && adjTrees[i].isEmpty()) { s[i] = WHITE; changed = true }
            if (s[i] != SHADED) continue
            for (j in kingNb[i]) {
                if (s[j] == SHADED) return CONTRA
                if (s[j] == UNKNOWN) { s[j] = WHITE; changed = true }
            }
        }
        for (k in 0 until 2 * n) {
            val c = counts[k]
            if (c < 0) continue
            var tents = 0
            var open = 0
            for (j in lines[k]) if (s[j] == SHADED) tents++ else if (s[j] == UNKNOWN) open++
            if (tents > c || tents + open < c) return CONTRA
            if (open == 0) continue
            val v = if (tents == c) WHITE else if (tents + open == c) SHADED else continue
            for (j in lines[k]) if (s[j] == UNKNOWN) s[j] = v
            changed = true
        }
        return if (changed) CHANGED else NONE
    }

    /** Level 1: tree-tent matching. */
    private fun match(s: IntArray): Int {
        var changed = false
        var tents = 0
        for (i in 0 until cells) if (s[i] == SHADED) tents++
        if (tents > trees.size) return CONTRA
        if (tents == trees.size) {
            for (i in 0 until cells) if (s[i] == UNKNOWN) { s[i] = WHITE; changed = true }
            if (changed) return CHANGED
        }
        for (t in trees) {
            val nb = treeNb.getValue(t)
            val possible = nb.filter { s[it] != WHITE }
            if (possible.isEmpty()) return CONTRA
            if (possible.size == 1 && s[possible[0]] == UNKNOWN) { s[possible[0]] = SHADED; changed = true; continue }
        }
        if (changed) return CHANGED
        return if (matchSize(s) < trees.size) CONTRA else NONE
    }

    private fun whatIf(s: IntArray): Int {
        var changed = false
        for (i in 0 until cells) {
            if (s[i] != UNKNOWN) continue
            val bad = BooleanArray(2)
            for (v in intArrayOf(WHITE, SHADED)) {
                val t = s.copyOf()
                t[i] = v
                bad[v] = propagate(t, level >= 3, true) == CONTRA
            }
            if (bad[WHITE] && bad[SHADED]) return CONTRA
            if (bad[WHITE]) { s[i] = SHADED; changed = true }
            else if (bad[SHADED]) { s[i] = WHITE; changed = true }
        }
        return if (changed) CHANGED else NONE
    }

    companion object {
        private const val CONTRA = -1
        private const val NONE = 0
        private const val CHANGED = 1

        fun forLevel(d: Difficulty, n: Int, numbers: IntArray, counts: IntArray) = TentsLogic(n, numbers, counts, d.ordinal)
    }
}

object TentsGenerator {
    fun size(d: Difficulty) = intArrayOf(6, 8, 10, 12)[d.ordinal]

    /** Counts to keep at least (of the 2n lines): easier levels stop hiding counts early. */
    fun minClues(d: Difficulty, n: Int) = when (d) {
        Difficulty.EASY -> (2 * n * 0.8).toInt()
        Difficulty.MEDIUM -> (2 * n * 0.6).toInt()
        else -> 0
    }

    private class Layout(val n: Int, val tent: BooleanArray, val numbers: IntArray, val counts: IntArray)

    /** Non-touching tents at random, a tree next to each one, counts from the tents. Null if a tent finds no tree spot. */
    private fun layout(n: Int, random: Random): Layout? {
        val target = maxOf(3, (n * n * (0.16 + random.nextDouble() * 0.06)).toInt())
        val tent = BooleanArray(n * n)
        val blocked = BooleanArray(n * n)
        var placed = 0
        for (i in (0 until n * n).shuffled(random)) {
            if (placed >= target) break
            if (blocked[i]) continue
            tent[i] = true
            placed++
            blocked[i] = true
            for (j in king(i, n)) blocked[j] = true
        }
        val tree = BooleanArray(n * n)
        for (i in (0 until n * n).filter { tent[it] }.shuffled(random)) {
            val spots = orth(i, n).filter { !tent[it] && !tree[it] }
            if (spots.isEmpty()) return null
            tree[spots.random(random)] = true
        }
        val counts = IntArray(2 * n) { k ->
            if (k < n) (0 until n).count { tent[k * n + it] } else (0 until n).count { tent[it * n + (k - n)] }
        }
        return Layout(n, tent, IntArray(n * n) { if (tree[it]) TREE else 0 }, counts)
    }

    private fun removeCounts(d: Difficulty, n: Int, l: Layout, counts: IntArray, start: IntArray, random: Random, end: Long) {
        var left = counts.count { it >= 0 }
        val floor = minClues(d, n)
        for (k in (0 until 2 * n).shuffled(random)) {
            if (past(end) || left <= floor) break
            val old = counts[k]
            counts[k] = -1
            if (!TentsLogic.forLevel(d, n, l.numbers, counts).run(start).solved) counts[k] = old else left--
        }
    }

    private fun needsTop(d: Difficulty, n: Int, l: Layout, counts: IntArray, start: IntArray) =
        d.ordinal < 2 || !TentsLogic.forLevel(Difficulty.entries[d.ordinal - 1], n, l.numbers, counts).run(start).solved

    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000, n: Int = size(d)): ShadePuzzle {
        val end = deadline(budgetMs)
        // Easy and Medium look for a given-free layout for a short while, then give tents away; Expert only prefers its top level.
        val quitEnd = if (d == Difficulty.EASY || d == Difficulty.MEDIUM) deadline(minOf(budgetMs, 500)) else end
        val preferEnd = if (d == Difficulty.EXPERT) deadline(minOf(budgetMs, 1000)) else end
        val unknown = IntArray(n * n) { UNKNOWN }
        var best: ShadePuzzle? = null
        var fallback: Layout? = null
        do {
            val l = layout(n, random) ?: continue
            if (fallback == null) fallback = l
            if (!TentsLogic.forLevel(d, n, l.numbers, l.counts).run(unknown).solved) continue
            val counts = l.counts.copyOf()
            removeCounts(d, n, l, counts, unknown, random, end)
            val p = ShadePuzzle(Variant.TENTS, d, n, l.tent, BooleanArray(n * n), numbers = l.numbers, counts = counts)
            if (needsTop(d, n, l, counts, unknown)) return p
            best = p
        } while (!past(end) && !(best != null && past(preferEnd)) && !past(quitEnd))
        best?.let { return it }
        // No layout solved on its own: give cells away until the full-count puzzle solves, then hide counts.
        val l = fallback ?: layout(n, random) ?: error("no tents layout")
        val givens = BooleanArray(n * n)
        val logic = TentsLogic.forLevel(d, n, l.numbers, l.counts)
        var start: IntArray
        while (true) {
            start = IntArray(n * n) { if (givens[it]) (if (l.tent[it]) SHADED else WHITE) else UNKNOWN }
            val result = logic.run(start)
            if (result.solved) break
            val open = result.unresolved.ifEmpty { givens.indices.filter { !givens[it] && l.numbers[it] != TREE } }
            givens[open.filter { l.tent[it] }.randomOrNull(random) ?: open.randomOrNull(random) ?: break] = true
        }
        val counts = l.counts.copyOf()
        removeCounts(d, n, l, counts, start, random, deadline(budgetMs))
        return ShadePuzzle(Variant.TENTS, d, n, l.tent, givens, numbers = l.numbers, counts = counts)
    }
}
