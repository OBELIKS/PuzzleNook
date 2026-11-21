package com.puzzlenook.region

import com.puzzlenook.sudoku.Difficulty
import kotlin.random.Random

/** A rectangle of grid cells, rows [r0]..[r1] and columns [c0]..[c1] (inclusive). Grids are at most 15 wide. */
data class Rect(val r0: Int, val c0: Int, val r1: Int, val c1: Int) {
    val h: Int get() = r1 - r0 + 1
    val w: Int get() = c1 - c0 + 1
    val area: Int get() = h * w

    /** One int per rectangle, for saving and for maps. */
    val code: Int get() = ((r0 * 16 + c0) * 16 + r1) * 16 + c1

    fun contains(r: Int, c: Int) = r in r0..r1 && c in c0..c1
    fun containsCell(cell: Int, n: Int) = contains(cell / n, cell % n)
    fun overlaps(o: Rect) = r0 <= o.r1 && o.r0 <= r1 && c0 <= o.c1 && o.c0 <= c1

    fun cells(n: Int): IntArray {
        val out = IntArray(area)
        var k = 0
        for (r in r0..r1) for (c in c0..c1) out[k++] = r * n + c
        return out
    }

    companion object {
        fun fromCode(code: Int) = Rect((code shr 12) and 15, (code shr 8) and 15, (code shr 4) and 15, code and 15)

        /** The rectangle spanned by two cells (any corners). */
        fun between(a: Int, b: Int, n: Int) =
            Rect(minOf(a / n, b / n), minOf(a % n, b % n), maxOf(a / n, b / n), maxOf(a % n, b % n))
    }
}

/**
 * A Shikaku puzzle: [numbers] is the area on each clue cell (0 elsewhere); [solution] is the partition
 * into rectangles, each holding exactly one clue equal to its area.
 */
class ShikakuPuzzle(
    val difficulty: Difficulty,
    val n: Int,
    val numbers: IntArray,
    val solution: List<Rect>,
)

/** Where [ShikakuLogic] got to: the rectangle settled for each clue cell, and the round it was settled in. */
class ShikakuResult(val solved: Boolean, val settled: Map<Int, Rect>, val round: Map<Int, Int>) {
    fun unresolved(clues: List<Int>) = clues.filter { it !in settled }
}

/**
 * Shikaku logic. Every clue keeps a set of candidate rectangles (the right area, no other clue inside).
 *
 * Levels (0 = Easy ... 3 = Expert):
 * - 0: a clue with one candidate is placed and its cells leave every other clue's candidates; a cell
 *   that only one clue's candidates cover fixes that clue to the candidates covering it; a cell no
 *   candidate covers is a contradiction;
 * - 1: adds forced cells: the cells every candidate of a clue covers are that clue's, and candidates
 *   of other clues that overlap them go;
 * - 2: as 1, plus a bounded what-if on single candidates: the trial runs at most `TRIAL_SWEEPS` sweeps
 *   of the level 0 steps and counts only a contradiction found within them;
 * - 3: as 1, plus branch intersection over the level 1 steps ([branches]): every candidate of a clue is
 *   propagated; contradicting ones go, and each clue keeps only what some surviving option leaves it.
 */
class ShikakuLogic(private val n: Int, numbers: IntArray, private val level: Int) {
    private val cells = n * n
    val clues: List<Int> = (0 until cells).filter { numbers[it] > 0 }
    private val start: List<List<Rect>> = clues.map { candidatesFor(it, numbers) }

    private fun candidatesFor(clue: Int, numbers: IntArray): List<Rect> {
        val area = numbers[clue]
        val cr = clue / n
        val cc = clue % n
        val out = ArrayList<Rect>()
        for (h in 1..area) {
            if (area % h != 0) continue
            val w = area / h
            for (r0 in maxOf(0, cr - h + 1)..minOf(cr, n - h)) for (c0 in maxOf(0, cc - w + 1)..minOf(cc, n - w)) {
                val rect = Rect(r0, c0, r0 + h - 1, c0 + w - 1)
                var other = false
                for (r in rect.r0..rect.r1) for (c in rect.c0..rect.c1) if ((r != cr || c != cc) && numbers[r * n + c] > 0) other = true
                if (!other) out += rect
            }
        }
        return out
    }

    /** Runs the logic; [placed] maps clue cells to rectangles already known to be right. */
    fun run(placed: Map<Int, Rect> = emptyMap()): ShikakuResult {
        val c = Array(clues.size) { i -> placed[clues[i]]?.takeIf { it in start[i] }?.let { arrayListOf(it) } ?: ArrayList(start[i]) }
        val round = HashMap<Int, Int>()
        var r = 0
        fun note() {
            for (i in c.indices) if (c[i].size == 1 && clues[i] !in round) round[clues[i]] = r
        }
        note()
        while (true) {
            r++
            var res = step(c, level >= 1)
            if (res == NONE && level >= 2) res = if (level >= 3) branches(c) else whatIf(c)
            if (res == CONTRA) return result(c, round, false)
            note()
            if (c.all { it.size == 1 }) return result(c, round, true)
            if (res == NONE) return result(c, round, false)
        }
    }

    private fun result(c: Array<ArrayList<Rect>>, round: Map<Int, Int>, solved: Boolean): ShikakuResult {
        val settled = HashMap<Int, Rect>()
        for (i in c.indices) if (c[i].size == 1) settled[clues[i]] = c[i][0]
        return ShikakuResult(solved, settled, round)
    }

    /** One sweep of the rules; the result says whether anything changed or a contradiction came up. */
    private fun step(c: Array<ArrayList<Rect>>, forced: Boolean): Int {
        var changed = false
        val owner = IntArray(cells) { -1 }
        val count = IntArray(cells)
        for (i in c.indices) {
            val list = c[i]
            if (list.isEmpty()) return CONTRA
            if (list.size > 1 && !forced) continue
            java.util.Arrays.fill(count, 0)
            for (rect in list) for (rr in rect.r0..rect.r1) for (cc in rect.c0..rect.c1) count[rr * n + cc]++
            val first = list[0]
            for (rr in first.r0..first.r1) for (cc in first.c0..first.c1) {
                val cell = rr * n + cc
                if (count[cell] == list.size) {
                    if (owner[cell] >= 0 && owner[cell] != i) return CONTRA
                    owner[cell] = i
                }
            }
        }
        for (i in c.indices) {
            val list = c[i]
            val before = list.size
            list.removeAll { rect ->
                var bad = false
                for (rr in rect.r0..rect.r1) {
                    for (cc in rect.c0..rect.c1) {
                        val o = owner[rr * n + cc]
                        if (o >= 0 && o != i) { bad = true; break }
                    }
                    if (bad) break
                }
                bad
            }
            if (list.isEmpty()) return CONTRA
            if (list.size != before) changed = true
        }
        // Cells covered by one clue's candidates only.
        val only = IntArray(cells) { -1 }
        for (i in c.indices) for (rect in c[i]) for (rr in rect.r0..rect.r1) for (cc in rect.c0..rect.c1) {
            val cell = rr * n + cc
            if (only[cell] == -1) only[cell] = i else if (only[cell] != i) only[cell] = -2
        }
        for (cell in 0 until cells) {
            val i = only[cell]
            if (i == -1) return CONTRA
            if (i < 0) continue
            val r = cell / n
            val col = cell % n
            val before = c[i].size
            c[i].removeAll { !it.contains(r, col) }
            if (c[i].isEmpty()) return CONTRA
            if (c[i].size != before) changed = true
        }
        return if (changed) CHANGED else NONE
    }

    private fun whatIf(c: Array<ArrayList<Rect>>): Int {
        var changed = false
        val trialForced = false
        for (i in c.indices) {
            if (c[i].size < 2) continue
            for (rect in c[i].toList()) {
                val t = Array(c.size) { ArrayList(c[it]) }
                t[i] = arrayListOf(rect)
                var res: Int
                var sweeps = 0
                do { res = step(t, trialForced) } while (res == CHANGED && ++sweeps < TRIAL_SWEEPS)
                if (res == CONTRA) {
                    c[i].remove(rect)
                    changed = true
                    if (c[i].isEmpty()) return CONTRA
                }
            }
        }
        return if (changed) CHANGED else NONE
    }

    /**
     * Level 3: branch intersection. For each clue with two or more candidates, every candidate is
     * propagated with the level 1 steps; candidates that contradict drop out, and every clue keeps only
     * the candidates that appear in at least one surviving option (what all survivors share is settled).
     */
    private fun branches(c: Array<ArrayList<Rect>>): Int {
        var changed = false
        for (i in c.indices) {
            if (c[i].size < 2) continue
            val keep = Array(c.size) { HashSet<Rect>() }
            var alive = 0
            for (rect in c[i].toList()) {
                val t = Array(c.size) { ArrayList(c[it]) }
                t[i] = arrayListOf(rect)
                var res: Int
                do { res = step(t, true) } while (res == CHANGED)
                if (res == CONTRA) continue
                alive++
                for (j in t.indices) keep[j].addAll(t[j])
            }
            if (alive == 0) return CONTRA
            for (j in c.indices) {
                if (c[j].size < 2 && j != i) continue
                if (c[j].retainAll(keep[j])) changed = true
                if (c[j].isEmpty()) return CONTRA
            }
        }
        return if (changed) CHANGED else NONE
    }

    companion object {
        private const val CONTRA = -1
        private const val NONE = 0
        private const val CHANGED = 1

        /**
         * Level 2 what-if: a trial runs at most this many sweeps of `step`. 2 keeps Hard needing
         * level 2 (5/5) with fast generation; no value makes Expert need level 3 (batch 5b).
         */
        internal const val TRIAL_SWEEPS = 2

        fun forLevel(d: Difficulty, n: Int, numbers: IntArray) = ShikakuLogic(n, numbers, d.ordinal)
    }
}

/** Shikaku (home section "Regions"): a random partition, then moves clues until the level's logic solves. */
object ShikakuGenerator {
    fun size(d: Difficulty) = intArrayOf(6, 8, 10, 12)[d.ordinal]

    /** Largest rectangle area; bigger rectangles leave fewer clues and more room for deduction. */
    fun maxArea(d: Difficulty) = intArrayOf(6, 8, 10, 16)[d.ordinal]

    private fun now() = System.nanoTime()
    private fun past(deadline: Long) = now() > deadline
    private fun deadline(ms: Long) = now() + ms * 1_000_000

    /** A random partition: pick a free cell, then a free rectangle through it. Few single cells. */
    private fun partition(n: Int, maxArea: Int, random: Random): List<Rect> {
        val taken = BooleanArray(n * n)
        val out = ArrayList<Rect>()
        for (cell in (0 until n * n).shuffled(random)) {
            if (taken[cell]) continue
            val r = cell / n
            val c = cell % n
            val options = HashMap<Int, MutableList<Rect>>()
            for (h in 1..minOf(maxArea, n)) for (w in 1..minOf(maxArea / h, n)) {
                val area = h * w
                if (area == 1) continue
                for (r0 in maxOf(0, r - h + 1)..minOf(r, n - h)) for (c0 in maxOf(0, c - w + 1)..minOf(c, n - w)) {
                    var free = true
                    loop@ for (rr in r0 until r0 + h) for (cc in c0 until c0 + w) if (taken[rr * n + cc]) { free = false; break@loop }
                    if (free) options.getOrPut(area) { ArrayList() } += Rect(r0, c0, r0 + h - 1, c0 + w - 1)
                }
            }
            val rect = if (options.isEmpty()) Rect(r, c, r, c) else {
                val area = options.keys.sorted().let { it[random.nextInt(it.size)] }
                options.getValue(area).random(random)
            }
            for (rr in rect.r0..rect.r1) for (cc in rect.c0..rect.c1) taken[rr * n + cc] = true
            out += rect
        }
        return out
    }

    private fun numbersOf(n: Int, rects: List<Rect>, clue: IntArray): IntArray {
        val numbers = IntArray(n * n)
        for (i in rects.indices) numbers[clue[i]] = rects[i].area
        return numbers
    }

    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000, n: Int = size(d)): ShikakuPuzzle {
        val end = deadline(budgetMs)
        val preferEnd = if (d == Difficulty.EXPERT) deadline(minOf(budgetMs, 1000)) else end
        var best: ShikakuPuzzle? = null
        do {
            val rects = partition(n, maxArea(d), random)
            val clue = IntArray(rects.size) { rects[it].cells(n).random(random) }
            var numbers = numbersOf(n, rects, clue)
            var res = ShikakuLogic.forLevel(d, n, numbers).run()
            // Move the clues of rectangles the logic can't settle.
            var tries = 0
            while (!res.solved && tries++ < 25 && !past(end)) {
                for (i in rects.indices) if (clue[i] !in res.settled) clue[i] = rects[i].cells(n).random(random)
                numbers = numbersOf(n, rects, clue)
                res = ShikakuLogic.forLevel(d, n, numbers).run()
            }
            val puzzle = ShikakuPuzzle(d, n, numbers, rects)
            if (!res.solved) continue
            if (d.ordinal >= 2) {
                // Wander the clues (keeping the puzzle solvable at this level) towards one that needs the top level.
                var steps = 0
                var needs = needsTop(d, n, numbers)
                while (!needs && steps++ < 40 && !past(end) && !(best != null && past(preferEnd))) {
                    val i = rects.indices.random(random)
                    val old = clue[i]
                    numbers[old] = 0
                    clue[i] = rects[i].cells(n).random(random)
                    numbers[clue[i]] = rects[i].area
                    if (ShikakuLogic.forLevel(d, n, numbers).run().solved) {
                        needs = needsTop(d, n, numbers)
                    } else {
                        numbers[clue[i]] = 0
                        clue[i] = old
                        numbers[old] = rects[i].area
                    }
                }
                val p = ShikakuPuzzle(d, n, numbers.copyOf(), rects)
                if (needs) return p
                best = p
            } else return puzzle
        } while (!past(end) && !(best != null && past(preferEnd)))
        best?.let { return it }
        // Nothing solved in time: smaller rectangles, until the logic settles a partition.
        while (true) {
            val rects = partition(n, 4, random)
            val clue = IntArray(rects.size) { rects[it].cells(n).random(random) }
            var numbers = numbersOf(n, rects, clue)
            var res = ShikakuLogic.forLevel(d, n, numbers).run()
            var tries = 0
            while (!res.solved && tries++ < 25) {
                for (i in rects.indices) if (clue[i] !in res.settled) clue[i] = rects[i].cells(n).random(random)
                numbers = numbersOf(n, rects, clue)
                res = ShikakuLogic.forLevel(d, n, numbers).run()
            }
            if (res.solved) return ShikakuPuzzle(d, n, numbers, rects)
        }
    }

    private fun needsTop(d: Difficulty, n: Int, numbers: IntArray) =
        !ShikakuLogic.forLevel(Difficulty.entries[d.ordinal - 1], n, numbers).run().solved
}
