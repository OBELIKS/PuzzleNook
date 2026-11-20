package com.puzzlenook.bridges

import com.puzzlenook.sudoku.Difficulty
import kotlin.random.Random

/** A place where a bridge could go: two islands facing each other along a row or column ([a] < [b] are cells). */
class BridgeSlot(val a: Int, val b: Int, val ia: Int, val ib: Int, val horizontal: Boolean)

/**
 * The islands of a Bridges puzzle ([numbers]: the bridge count on an island cell, 0 elsewhere) and every
 * slot between two islands that face each other with at least one cell between them.
 * Directions: 0 up, 1 right, 2 down, 3 left.
 */
class BridgeLayout(val n: Int, val numbers: IntArray) {
    val islands: IntArray = (0 until n * n).filter { numbers[it] > 0 }.toIntArray()
    val islandOf = IntArray(n * n) { -1 }.also { for ((i, c) in islands.withIndex()) it[c] = i }
    val slots: List<BridgeSlot>
    val slotsAt: Array<IntArray>
    val dirSlot: Array<IntArray> = Array(islands.size) { IntArray(4) { -1 } }
    val crossing: Array<IntArray>

    init {
        val list = ArrayList<BridgeSlot>()
        for ((i, cell) in islands.withIndex()) {
            val r = cell / n
            val c = cell % n
            var cc = c + 1
            while (cc < n && islandOf[r * n + cc] < 0) cc++
            if (cc < n && cc > c + 1) {
                val j = islandOf[r * n + cc]
                dirSlot[i][1] = list.size
                dirSlot[j][3] = list.size
                list += BridgeSlot(cell, r * n + cc, i, j, true)
            }
            var rr = r + 1
            while (rr < n && islandOf[rr * n + c] < 0) rr++
            if (rr < n && rr > r + 1) {
                val j = islandOf[rr * n + c]
                dirSlot[i][2] = list.size
                dirSlot[j][0] = list.size
                list += BridgeSlot(cell, rr * n + c, i, j, false)
            }
        }
        slots = list
        val at = Array(islands.size) { ArrayList<Int>() }
        for ((s, slot) in slots.withIndex()) {
            at[slot.ia] += s
            at[slot.ib] += s
        }
        slotsAt = Array(islands.size) { at[it].toIntArray() }
        crossing = Array(slots.size) { s ->
            val x = slots[s]
            slots.indices.filter { t -> crosses(x, slots[t]) }.toIntArray()
        }
    }

    private fun crosses(x: BridgeSlot, y: BridgeSlot): Boolean {
        if (x.horizontal == y.horizontal) return false
        val h = if (x.horizontal) x else y
        val v = if (x.horizontal) y else x
        val row = h.a / n
        val col = v.a % n
        return col > h.a % n && col < h.b % n && row > v.a / n && row < v.b / n
    }

    /** The cells strictly between the two islands of slot [s]. */
    fun between(s: Int): IntArray {
        val x = slots[s]
        val step = if (x.horizontal) 1 else n
        return IntArray((x.b - x.a) / step - 1) { x.a + step * (it + 1) }
    }
}

/** A Bridges puzzle: [solution] is the bridge count (0..2) of every slot of `BridgeLayout(n, numbers)`. */
class BridgesPuzzle(
    val difficulty: Difficulty,
    val n: Int,
    val numbers: IntArray,
    val solution: IntArray,
)

/** Where [BridgesLogic] got to: the value settled for each slot (-1 = open) and the round it was settled in. */
class BridgesResult(val solved: Boolean, val values: IntArray, val round: IntArray)

/**
 * Bridges logic. Every slot keeps the set of counts (0, 1, 2) it may still have (a bit mask).
 *
 * Levels (0 = Easy ... 3 = Expert):
 * - 0: capacity: an island's slots must add up to its number, so each slot's count is limited by what
 *   the island's other slots can still give or must give;
 * - 1: adds crossings (a bridge that is certain rules out the slots crossing it) and isolation (a count
 *   that would close a finished group off from the other islands goes; a group with one way out takes it);
 * - 2: as 1, plus what-if on single slot counts whose trial uses only the level 0 steps;
 * - 3: as 1, plus what-if on single slot counts whose trial uses the level 0 and 1 steps.
 */
class BridgesLogic(private val layout: BridgeLayout, private val level: Int) {
    private val slots = layout.slots
    private val m = layout.islands.size
    private val num = IntArray(m) { layout.numbers[layout.islands[it]] }

    private fun lo(d: Int) = if (d and 1 != 0) 0 else if (d and 2 != 0) 1 else 2
    private fun hi(d: Int) = if (d and 4 != 0) 2 else if (d and 2 != 0) 1 else 0

    /** Runs the logic; [placed] maps slots to counts already known to be right. */
    fun run(placed: Map<Int, Int> = emptyMap()): BridgesResult {
        val dom = IntArray(slots.size) { 7 }
        for ((s, v) in placed) if (s in dom.indices && v in 0..2) dom[s] = 1 shl v
        val round = IntArray(slots.size) { -1 }
        var r = 0
        fun note() {
            for (s in dom.indices) if (round[s] < 0 && Integer.bitCount(dom[s]) == 1) round[s] = r
        }
        note()
        while (true) {
            r++
            var res = step(dom, level >= 1)
            if (res == NONE && level >= 2) res = whatIf(dom)
            if (res == CONTRA) return result(dom, round, false)
            note()
            if (dom.all { Integer.bitCount(it) == 1 }) return result(dom, round, true)
            if (res == NONE) return result(dom, round, false)
        }
    }

    private fun result(dom: IntArray, round: IntArray, solved: Boolean) =
        BridgesResult(solved, IntArray(dom.size) { if (Integer.bitCount(dom[it]) == 1) lo(dom[it]) else -1 }, round)

    /** One sweep of the rules; the result says whether anything changed or a contradiction came up. */
    private fun step(dom: IntArray, forced: Boolean): Int {
        var changed = false
        for (i in 0 until m) {
            val k = num[i]
            val at = layout.slotsAt[i]
            var smin = 0
            var smax = 0
            for (s in at) { smin += lo(dom[s]); smax += hi(dom[s]) }
            if (smin > k || smax < k) return CONTRA
            for (s in at) {
                val d = dom[s]
                val omin = smin - lo(d)
                val omax = smax - hi(d)
                var nd = d
                for (v in 0..2) if (d and (1 shl v) != 0 && (v + omin > k || v + omax < k)) nd = nd and (1 shl v).inv()
                if (nd == 0) return CONTRA
                if (nd != d) {
                    dom[s] = nd
                    changed = true
                    smin += lo(nd) - lo(d)
                    smax += hi(nd) - hi(d)
                }
            }
        }
        if (forced) {
            for (s in slots.indices) {
                if (dom[s] and 1 != 0) continue
                for (t in layout.crossing[s]) {
                    val d = dom[t] and 1
                    if (d == 0) return CONTRA
                    if (d != dom[t]) { dom[t] = d; changed = true }
                }
            }
            val iso = isolation(dom)
            if (iso == CONTRA) return CONTRA
            if (iso == CHANGED) changed = true
        }
        return if (changed) CHANGED else NONE
    }

    private val parent = IntArray(m)
    private fun find(x: Int): Int {
        var a = x
        while (parent[a] != a) { parent[a] = parent[parent[a]]; a = parent[a] }
        return a
    }

    /** Groups of islands joined by certain bridges, with slot [override] counted as [value] (-1: none). */
    private fun group(dom: IntArray, override: Int, value: Int) {
        for (i in 0 until m) parent[i] = i
        for (s in slots.indices) {
            val v = if (s == override) value else lo(dom[s])
            if (v >= 1) parent[find(slots[s].ia)] = find(slots[s].ib)
        }
    }

    private fun isolation(dom: IntArray): Int {
        if (m <= 1) return NONE
        var changed = false
        for (s in slots.indices) {
            var d = dom[s]
            for (v in 1..2) {
                if (d and (1 shl v) == 0) continue
                group(dom, s, v)
                val root = find(slots[s].ia)
                var size = 0
                var closed = true
                for (i in 0 until m) {
                    if (find(i) != root) continue
                    size++
                    var sum = 0
                    for (t in layout.slotsAt[i]) sum += if (t == s) v else lo(dom[t])
                    if (sum != num[i]) closed = false
                }
                if (closed && size < m) d = d and (1 shl v).inv()
            }
            if (d == 0) return CONTRA
            if (d != dom[s]) { dom[s] = d; changed = true }
        }
        // A group of islands needs a way out: no way out is a contradiction, a single one is taken.
        group(dom, -1, 0)
        val size = IntArray(m)
        for (i in 0 until m) size[find(i)]++
        val exits = IntArray(m)
        val lastExit = IntArray(m) { -1 }
        for (s in slots.indices) {
            if (hi(dom[s]) == 0) continue
            val x = find(slots[s].ia)
            val y = find(slots[s].ib)
            if (x == y) continue
            exits[x]++; lastExit[x] = s
            exits[y]++; lastExit[y] = s
        }
        for (root in 0 until m) {
            if (size[root] == 0 || size[root] == m) continue
            if (exits[root] == 0) return CONTRA
            if (exits[root] == 1) {
                val s = lastExit[root]
                if (dom[s] and 1 != 0) { dom[s] = dom[s] and 1.inv(); changed = true }
            }
        }
        return if (changed) CHANGED else NONE
    }

    private fun whatIf(dom: IntArray): Int {
        var changed = false
        val trialForced = level >= 3
        for (s in slots.indices) {
            if (Integer.bitCount(dom[s]) < 2) continue
            for (v in 0..2) {
                if (dom[s] and (1 shl v) == 0) continue
                val t = dom.copyOf()
                t[s] = 1 shl v
                var res: Int
                do { res = step(t, trialForced) } while (res == CHANGED)
                if (res == CONTRA) {
                    dom[s] = dom[s] and (1 shl v).inv()
                    changed = true
                    if (dom[s] == 0) return CONTRA
                }
            }
        }
        return if (changed) CHANGED else NONE
    }

    companion object {
        private const val CONTRA = -1
        private const val NONE = 0
        private const val CHANGED = 1

        fun forLevel(d: Difficulty, layout: BridgeLayout) = BridgesLogic(layout, d.ordinal)
    }
}

object BridgesGenerator {
    fun size(d: Difficulty) = intArrayOf(7, 9, 11, 13)[d.ordinal]

    /** Islands aimed for: about a fifth of the cells. */
    fun targetIslands(n: Int) = n * n / 5

    /** Easy and Medium never get fewer islands than this (a bare puzzle is no fun). */
    fun minIslands(n: Int) = n * n / 6

    private fun now() = System.nanoTime()
    private fun past(deadline: Long) = now() > deadline
    private fun deadline(ms: Long) = now() + ms * 1_000_000

    private val DR = intArrayOf(-1, 0, 1, 0)
    private val DC = intArrayOf(0, 1, 0, -1)

    private class Built(val numbers: IntArray, val solution: IntArray, val layout: BridgeLayout)

    /** Grows a random connected island and bridge layout without crossings, then adds a few extra bridges. */
    private fun build(n: Int, target: Int, random: Random): Built? {
        val island = BooleanArray(n * n)
        val occupied = BooleanArray(n * n)
        val counts = HashMap<Long, Int>()
        fun key(a: Int, b: Int) = minOf(a, b).toLong() * 1024 + maxOf(a, b)
        val list = ArrayList<Int>()
        val start = random.nextInt(n * n)
        island[start] = true
        list += start
        fun adjacentIsland(cell: Int): Boolean {
            val r = cell / n
            val c = cell % n
            for (k in 0..3) {
                val rr = r + DR[k]
                val cc = c + DC[k]
                if (rr in 0 until n && cc in 0 until n && island[rr * n + cc]) return true
            }
            return false
        }
        var fails = 0
        while (list.size < target && fails < 300) {
            val from = list[random.nextInt(list.size)]
            val k = random.nextInt(4)
            val ends = ArrayList<Int>()
            var r = from / n + DR[k]
            var c = from % n + DC[k]
            var len = 1
            while (r in 0 until n && c in 0 until n && !island[r * n + c] && !occupied[r * n + c]) {
                len++
                if (len >= 2 && !adjacentIsland(r * n + c)) ends += r * n + c
                r += DR[k]
                c += DC[k]
            }
            if (ends.isEmpty()) { fails++; continue }
            val end = ends[random.nextInt(ends.size)]
            val step = DR[k] * n + DC[k]
            var cell = from + step
            while (cell != end) { occupied[cell] = true; cell += step }
            island[end] = true
            list += end
            counts[key(from, end)] = if (random.nextInt(100) < 30) 2 else 1
        }
        if (list.size < minIslands(n)) return null
        // Extra bridges between facing islands make loops.
        val numbers0 = IntArray(n * n)
        for (cell in list) numbers0[cell] = 1
        val l0 = BridgeLayout(n, numbers0)
        for (s in l0.slots.indices.shuffled(random)) {
            val slot = l0.slots[s]
            if (key(slot.a, slot.b) in counts) continue
            if (random.nextInt(100) >= 45) continue
            val between = l0.between(s)
            if (between.any { occupied[it] }) continue
            for (cell in between) occupied[cell] = true
            counts[key(slot.a, slot.b)] = if (random.nextInt(100) < 30) 2 else 1
        }
        val numbers = IntArray(n * n)
        for ((kk, v) in counts) {
            numbers[(kk / 1024).toInt()] += v
            numbers[(kk % 1024).toInt()] += v
        }
        val layout = BridgeLayout(n, numbers)
        val solution = IntArray(layout.slots.size) { counts[key(layout.slots[it].a, layout.slots[it].b)] ?: 0 }
        return Built(numbers, solution, layout)
    }

    fun generate(d: Difficulty, random: Random = Random.Default, budgetMs: Long = 4000, n: Int = size(d)): BridgesPuzzle {
        val end = deadline(budgetMs)
        val preferEnd = if (d == Difficulty.EXPERT) deadline(minOf(budgetMs, 1000)) else end
        var best: BridgesPuzzle? = null
        val target = targetIslands(n)
        do {
            val b = build(n, target, random) ?: continue
            if (d.ordinal >= 2) {
                val lower = BridgesLogic.forLevel(Difficulty.entries[d.ordinal - 1], b.layout)
                if (lower.run().solved) {
                    if (best == null) best = BridgesPuzzle(d, n, b.numbers, b.solution)
                    continue
                }
                if (BridgesLogic.forLevel(d, b.layout).run().solved) return BridgesPuzzle(d, n, b.numbers, b.solution)
            } else if (BridgesLogic.forLevel(d, b.layout).run().solved) {
                return BridgesPuzzle(d, n, b.numbers, b.solution)
            }
        } while (!past(end) && !(best != null && past(preferEnd)))
        best?.let { return it }
        // Nothing solved in time: keep going until one does.
        while (true) {
            val b = build(n, target, random) ?: continue
            if (BridgesLogic.forLevel(d, b.layout).run().solved) return BridgesPuzzle(d, n, b.numbers, b.solution)
        }
    }
}
