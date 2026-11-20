package com.puzzlenook.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FillominoTest {
    private fun orth(n: Int, i: Int): List<Int> = Regions.neighbours(n, i)

    /** Independent rule check: every group of touching equal digits has exactly that many cells. */
    private fun assertValid(n: Int, grid: IntArray) {
        val seen = BooleanArray(n * n)
        for (i in 0 until n * n) {
            assertTrue("cell $i digit ${grid[i]}", grid[i] in 1..9)
            if (seen[i]) continue
            val stack = ArrayDeque<Int>()
            stack.add(i)
            seen[i] = true
            var count = 0
            while (stack.isNotEmpty()) {
                val x = stack.removeLast()
                count++
                for (y in orth(n, x)) if (!seen[y] && grid[y] == grid[i]) {
                    seen[y] = true
                    stack.add(y)
                }
            }
            assertEquals("group at cell $i", grid[i], count)
        }
    }

    private fun assertValid(p: Puzzle) {
        assertValid(p.size, p.solution)
        for (i in p.givens.indices) if (p.givens[i] != 0) assertEquals(p.solution[i], p.givens[i])
    }

    /**
     * Independent exact counter (shares nothing with FillominoLogic): partitions the grid into areas, always
     * starting at the first uncovered cell, trying every shape of every size that fits the shown digits and
     * doesn't touch an area of the same size. Stops at [limit].
     */
    private fun countSolutions(p: Puzzle, limit: Int = 2): Int {
        val n = p.size
        val cells = n * n
        val given = p.givens
        val covered = BooleanArray(cells)
        val touching = Array(cells) { IntArray(10) }
        var count = 0

        fun pocketsOk(): Boolean {
            val seen = BooleanArray(cells)
            for (i in 0 until cells) {
                if (covered[i] || seen[i]) continue
                val stack = ArrayDeque<Int>()
                stack.add(i)
                seen[i] = true
                var size = 0
                var need = 0
                while (stack.isNotEmpty()) {
                    val x = stack.removeLast()
                    size++
                    if (given[x] != 0) {
                        need = maxOf(need, given[x])
                        if (touching[x][given[x]] > 0) return false
                    }
                    for (y in orth(n, x)) if (!covered[y] && !seen[y]) {
                        seen[y] = true
                        stack.add(y)
                    }
                }
                if (size < need) return false
            }
            return true
        }

        fun rec() {
            if (count >= limit) return
            val first = covered.indexOfFirst { !it }
            if (first < 0) {
                count++
                return
            }
            if (!pocketsOk()) return
            for (k in 1..9) {
                if (given[first] != 0 && given[first] != k) continue
                fun allowed(c: Int) = !covered[c] && (given[c] == 0 || given[c] == k) && touching[c][k] == 0
                if (!allowed(first)) continue
                var layer = setOf(1L shl first)
                for (step in 1 until k) {
                    val next = HashSet<Long>()
                    for (m in layer) for (c in 0 until cells) {
                        if (m shr c and 1L == 0L) continue
                        for (y in orth(n, c)) if (m shr y and 1L == 0L && allowed(y)) next.add(m or (1L shl y))
                    }
                    layer = next
                    if (layer.isEmpty()) break
                }
                for (m in layer) {
                    val inside = (0 until cells).filter { m shr it and 1L == 1L }
                    val around = inside.flatMap { orth(n, it) }.filter { m shr it and 1L == 0L }.distinct()
                    // A shown k outside that touches this area would have to be an area of its own, of the same size.
                    if (around.any { !covered[it] && given[it] == k }) continue
                    for (c in inside) covered[c] = true
                    for (y in around) touching[y][k]++
                    rec()
                    for (y in around) touching[y][k]--
                    for (c in inside) covered[c] = false
                    if (count >= limit) return
                }
            }
        }
        rec()
        return count
    }

    @Test(timeout = 300_000) fun puzzlesAreValidAndSolvedByLogic() {
        for (d in Difficulty.entries) for (seed in 0 until 5) {
            val p = FillominoGenerator.generate(d, Random(seed + 7))
            assertValid(p)
            assertEquals(FillominoGenerator.size(d), p.size)
            val r = FillominoLogic.forLevel(d, p.size).run(p.givens)
            assertTrue("$d seed $seed solved by logic", r.solved)
            for (i in p.givens.indices) assertEquals("$d seed $seed cell $i", 1 shl p.solution[i], r.candidates[i])
            if (d.ordinal < 2) assertTrue("$d seed $seed givens ${p.clueCount}", p.clueCount >= FillominoGenerator.minGivens(d, p.size * p.size))
        }
    }

    /** Brute-force uniqueness on Easy (6x6) and Medium (7x7). */
    @Test(timeout = 300_000) fun easyAndMediumAreUnique() {
        for (d in listOf(Difficulty.EASY, Difficulty.MEDIUM)) for (seed in 0 until 6) {
            val p = FillominoGenerator.generate(d, Random(seed + 7))
            assertEquals("$d seed $seed unique", 1, countSolutions(p))
        }
    }

    /** The counter itself: a blank grid has many solutions, a contradicting given has none. */
    @Test(timeout = 300_000) fun counterFindsSeveralOrNoSolutions() {
        val p = FillominoGenerator.generate(Difficulty.EASY, Random(1))
        val blank = Puzzle(IntArray(p.size * p.size), p.solution, Difficulty.EASY, size = p.size)
        assertEquals(2, countSolutions(blank))
        val clash = IntArray(p.size * p.size)
        clash[0] = 1
        clash[1] = 1
        assertEquals(0, countSolutions(Puzzle(clash, p.solution, Difficulty.EASY, size = p.size)))
        assertEquals(1, countSolutions(Puzzle(p.solution, p.solution, Difficulty.EASY, size = p.size)))
    }

    /** Report only: how often Hard and Expert need their top level over 20 more seeds. */
    @Test(timeout = 300_000) fun topLevelOverExtraSeeds() {
        for (d in listOf(Difficulty.HARD, Difficulty.EXPERT)) {
            var top = 0
            var slowest = 0L
            for (seed in 100 until 120) {
                val t = System.currentTimeMillis()
                val p = FillominoGenerator.generate(d, Random(seed))
                slowest = maxOf(slowest, System.currentTimeMillis() - t)
                if (!FillominoLogic.forLevel(d.ordinal - 1, p.size).solves(p.givens)) top++
            }
            println("FILLOMINO extra $d top=$top/20 slowest=${slowest}ms")
        }
    }

    /** No level ever decides a cell against the solution, whatever part of it is already filled in. */
    @Test(timeout = 300_000) fun logicIsSound() {
        for (d in Difficulty.entries) {
            val p = FillominoGenerator.generate(d, Random(11))
            val n = p.size
            val random = Random(5)
            for (level in 0..3) repeat(if (level >= 2) 4 else 12) {
                val start = IntArray(n * n) { if (random.nextInt(100) < 30) p.solution[it] else 0 }
                val r = FillominoLogic.forLevel(level, n).run(start)
                for (i in 0 until n * n) if (Integer.bitCount(r.candidates[i]) == 1) {
                    assertEquals("$d level $level cell $i", 1 shl p.solution[i], r.candidates[i])
                } else assertTrue("$d level $level cell $i keeps the solution", r.candidates[i] and (1 shl p.solution[i]) != 0)
            }
        }
    }

    /** Givens floors, top-level need on Hard and Expert, generation times and level 3 solve times. */
    @Test(timeout = 300_000) fun levelsAndTimes() {
        for (d in Difficulty.entries) {
            val times = ArrayList<Long>()
            val given = ArrayList<Int>()
            val solveMs = ArrayList<Long>()
            var top = 0
            for (seed in 0 until 5) {
                val t = System.currentTimeMillis()
                val p = FillominoGenerator.generate(d, Random(seed + 7))
                times += System.currentTimeMillis() - t
                given += p.clueCount
                if (d.ordinal >= 2 && !FillominoLogic.forLevel(d.ordinal - 1, p.size).solves(p.givens)) top++
                if (d == Difficulty.EXPERT) {
                    val t3 = System.currentTimeMillis()
                    assertTrue("seed $seed level 3 solves", FillominoLogic.forLevel(3, p.size).solves(p.givens))
                    solveMs += System.currentTimeMillis() - t3
                }
            }
            times.sort()
            solveMs.sort()
            println("FILLOMINO $d median=${times[2]}ms max=${times.last()}ms givens=$given of ${FillominoGenerator.size(d).let { it * it }} top=$top/5 solve3=$solveMs")
            assertTrue("$d median ${times[2]}", times[2] <= 1500)
            assertTrue("$d max ${times.last()}", times.last() <= 5000)
            if (d == Difficulty.HARD) assertTrue("$d needs level 2 in $top/5", top >= 3)
            // Expert: report-only unless level 3 is reached.
            if (d == Difficulty.EXPERT && top > 0) assertTrue("$d needs level 3 in $top/5", top >= 3)
        }
    }

    /** Digit game state: no peers, so notes stay; hints follow the logic; the solution wins. */
    @Test(timeout = 300_000) fun stateHasNoPeersAndHintsFollowLogic() {
        val p = FillominoGenerator.generate(Difficulty.EASY, Random(3))
        val n = p.size
        var s = GameState.fromPuzzle(p, Variant.FILLOMINO)
        assertEquals(9, s.digits)
        assertTrue(s.units.isEmpty())
        assertFalse(s.isPeer(0, 1))
        assertEquals(null, s.regions)
        val empty = s.cells.indices.filter { s.cells[it].value == 0 }
        val a = empty[0]
        val b = empty[1]
        s = s.copy(cells = s.cells.mapIndexed { i, c -> if (i == b) c.copy(notes = (1 shl p.solution[a]) or 2) else c }, selected = a)
        s = s.enter(p.solution[a])
        assertTrue("notes elsewhere are never cleared", s.cells[b].hasNote(p.solution[a]))
        // The hint points at a cell the logic settles in its first round.
        val fresh = GameState.fromPuzzle(p, Variant.FILLOMINO)
        val board = IntArray(n * n) { fresh.cells[it].value }
        val r = FillominoLogic.forLevel(Difficulty.EASY, n).run(board)
        val first = fresh.cells.indices.filter { fresh.cells[it].value == 0 }.minOf { r.round[it] }
        val h = fresh.pickHintCell()
        assertNotNull(h)
        assertEquals(first, r.round[h!!])
        var w = fresh
        for (i in 0 until n * n) if (!w.cells[i].given) w = w.copy(selected = i).enter(p.solution[i])
        assertTrue(w.completed)
    }

    /** A position the game's level can't move on from (an old Hard puzzle played under Medium rules) still gets a hint on the solution path. */
    @Test(timeout = 300_000) fun hintFallsBackToNextLevel() {
        val p = FillominoGenerator.generate(Difficulty.HARD, Random(21))
        val n = p.size
        val stall = FillominoLogic.forLevel(1, n).run(p.givens)
        assertFalse("level 1 stalls", stall.solved)
        // The player has entered everything level 1 settles; the level 1 logic has nothing more to say.
        val board = IntArray(n * n) { if (Integer.bitCount(stall.candidates[it]) == 1) Integer.numberOfTrailingZeros(stall.candidates[it]) else 0 }
        assertTrue(board.indices.all { board[it] == 0 || board[it] == p.solution[it] })
        val own = FillominoLogic.forLevel(1, n).run(board)
        assertTrue("nothing new at level 1", board.indices.none { board[it] == 0 && own.round[it] > 0 })
        val s = GameState.fromPuzzle(p, Variant.FILLOMINO).copy(
            difficulty = Difficulty.MEDIUM,
            cells = board.indices.map { Cell(value = board[it], given = p.givens[it] != 0) },
        )
        val h = s.pickHintCell()!!
        assertEquals(0, board[h])
        val up = FillominoLogic.forLevel(2, n).run(board)
        val up3 = FillominoLogic.forLevel(3, n).run(board)
        assertTrue("hint $h settled by level 2 or 3", up.round[h] > 0 || up3.round[h] > 0)
    }
}