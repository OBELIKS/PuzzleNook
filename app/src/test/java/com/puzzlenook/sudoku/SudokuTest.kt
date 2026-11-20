package com.puzzlenook.sudoku

import com.puzzlenook.settings.HelpSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SudokuTest {

    private fun assertValidSolution(grid: IntArray) {
        for (unit in 0 until 9) {
            val row = (0 until 9).map { grid[unit * 9 + it] }.toSet()
            val col = (0 until 9).map { grid[it * 9 + unit] }.toSet()
            val box = (0 until 81).filter { SudokuSolver.box(it) == unit }.map { grid[it] }.toSet()
            assertEquals((1..9).toSet(), row)
            assertEquals((1..9).toSet(), col)
            assertEquals((1..9).toSet(), box)
        }
    }

    @Test
    fun randomSolvedGridIsValid() {
        repeat(20) { assertValidSolution(SudokuSolver.randomSolved(Random(it))) }
    }

    @Test
    fun generatedPuzzlesHaveUniqueSolutionMatchingSolution() {
        for (d in Difficulty.entries) {
            repeat(5) { seed ->
                val p = SudokuGenerator.generate(d, Random(seed * 31 + d.ordinal))
                assertValidSolution(p.solution)
                assertEquals(1, SudokuSolver.countSolutions(p.givens))
                assertTrue(SudokuSolver.solve(p.givens)!!.contentEquals(p.solution))
                for (i in 0 until 81) if (p.givens[i] != 0) assertEquals(p.solution[i], p.givens[i])
                assertTrue("$d had ${p.clueCount} clues", p.clueCount <= d.targetClues + 4)
            }
        }
    }

    @Test
    fun invalidBoardHasNoSolutions() {
        val board = IntArray(81).also { it[0] = 5; it[1] = 5 }
        assertEquals(0, SudokuSolver.countSolutions(board))
    }

    @Test
    fun enteringCorrectValueClearsPeerNotesAndWrongValueCountsMistake() {
        val p = SudokuGenerator.generate(Difficulty.EASY, Random(7))
        val empty = p.givens.indexOfFirst { it == 0 }
        val correct = p.solution[empty]
        val peer = (0 until 81).first { it != empty && p.givens[it] == 0 && GameState.peers(it, empty) }

        var s = GameState.fromPuzzle(p).copy(selected = peer, notesMode = true).enter(correct)
        assertTrue(s.cells[peer].hasNote(correct))

        s = s.copy(selected = empty, notesMode = false).enter(correct)
        assertFalse(s.cells[peer].hasNote(correct))
        assertEquals(0, s.mistakes)

        val wrong = (1..9).first { it != p.solution[peer] }
        s = s.copy(selected = peer).enter(wrong)
        assertEquals(1, s.mistakes)
        assertTrue(s.isWrong(peer))
    }

    @Test
    fun addNoteAddsWithoutTogglingAndSkipsFilledCells() {
        val p = SudokuGenerator.generate(Difficulty.EASY, Random(11))
        val empty = p.givens.indexOfFirst { it == 0 }
        var s = GameState.fromPuzzle(p).copy(selected = empty).addNote(4).addNote(4).addNote(7)
        assertTrue(s.cells[empty].hasNote(4))
        assertTrue(s.cells[empty].hasNote(7))
        assertFalse(s.notesMode)

        val filled = s.enter(p.solution[empty])
        assertEquals(filled, filled.addNote(2))
    }

    @Test
    fun nearestNoteFollowsTheNoteGrid() {
        val cell = Cell(notes = (1 shl 1) or (1 shl 5) or (1 shl 9))
        assertEquals(1, GameState.nearestNote(cell, 0.1f, 0.1f))   // top-left
        assertEquals(5, GameState.nearestNote(cell, 0.5f, 0.45f))  // centre
        assertEquals(9, GameState.nearestNote(cell, 0.9f, 0.85f))  // bottom-right
        assertEquals(9, GameState.nearestNote(cell, 0.6f, 0.95f))  // nearest existing (8 isn't there)
        assertEquals(null, GameState.nearestNote(Cell(), 0.5f, 0.5f))
    }

    @Test
    fun removeNoteRemovesOnlyThatNote() {
        val p = SudokuGenerator.generate(Difficulty.EASY, Random(12))
        val empty = p.givens.indexOfFirst { it == 0 }
        val s = GameState.fromPuzzle(p).copy(selected = empty).addNote(2).addNote(6).removeNote(2)
        assertFalse(s.cells[empty].hasNote(2))
        assertTrue(s.cells[empty].hasNote(6))
    }

    @Test
    fun hintLimitStopsNewHintsButNotReveal() {
        val p = SudokuGenerator.generate(Difficulty.MEDIUM, Random(5))
        val s0 = GameState.fromPuzzle(p).copy(help = HelpSettings(hintLimit = 1))
        val s1 = s0.hint()
        assertEquals(1, s1.hintsUsed)
        val s2 = s1.hint() // reveal still works
        assertEquals(null, s2.hintCell)
        assertEquals(2, s2.hintHalfPoints)
        val s3 = s2.hint() // second new hint does nothing
        assertEquals(s2, s3)
    }

    @Test
    fun risingCostChargesMorePerHint() {
        val p = SudokuGenerator.generate(Difficulty.MEDIUM, Random(5))
        var s = GameState.fromPuzzle(p).copy(help = HelpSettings(risingCost = true))
        repeat(4) { s = s.hint() } // two full hints: 1+1+2+2
        assertEquals(6, s.hintHalfPoints)
        assertEquals(2, s.hintsUsed)
    }

    @Test
    fun cooldownBlocksNewHintUntilTimePasses() {
        val p = SudokuGenerator.generate(Difficulty.MEDIUM, Random(5))
        var s = GameState.fromPuzzle(p).copy(help = HelpSettings(cooldownSeconds = 30), elapsedSeconds = 10)
        s = s.hint().hint() // hint and its reveal, not blocked
        assertEquals(10L, s.lastHintAt)
        val blocked = s.copy(elapsedSeconds = 20).hint()
        assertEquals(s.copy(elapsedSeconds = 20), blocked)
        val ok = s.copy(elapsedSeconds = 40).hint()
        assertEquals(2, ok.hintsUsed)
    }


    @Test
    fun hintsEventuallySolveThePuzzle() {
        val p = SudokuGenerator.generate(Difficulty.MEDIUM, Random(3))
        var s = GameState.fromPuzzle(p)
        while (!s.completed) s = s.hint()
        assertTrue(s.isSolved)
        // Every reveal took two presses: highlight (½) + reveal (½) = 1 point per cell.
        assertEquals(2 * (81 - p.clueCount), s.hintHalfPoints)
    }

    @Test
    fun firstHintHighlightsSolvableCellSecondRevealsIt() {
        val p = SudokuGenerator.generate(Difficulty.MEDIUM, Random(5))
        val s1 = GameState.fromPuzzle(p).hint()
        val cell = s1.hintCell!!
        assertEquals(0, s1.cells[cell].value)                 // nothing revealed yet
        assertEquals(1, s1.hintHalfPoints)                    // ½ point
        val s2 = s1.hint()
        assertEquals(p.solution[cell], s2.cells[cell].value)  // same cell revealed
        assertEquals(2, s2.hintHalfPoints)                    // 1 point total
        assertEquals(null, s2.hintCell)
    }

    @Test
    fun hintPointsAtWrongEntryFirst() {
        val p = SudokuGenerator.generate(Difficulty.EASY, Random(9))
        val empty = (0 until 81).filter { p.givens[it] == 0 }.last()
        val wrong = (1..9).first { it != p.solution[empty] }
        val s = GameState.fromPuzzle(p).copy(selected = empty).enter(wrong).copy(selected = 0).hint()
        assertEquals(empty, s.hintCell)
    }

    @Test
    fun solvingHighlightedCellYourselfClearsHint() {
        val p = SudokuGenerator.generate(Difficulty.EASY, Random(4))
        val s1 = GameState.fromPuzzle(p).hint()
        val cell = s1.hintCell!!
        val s2 = s1.copy(selected = cell).enter(p.solution[cell])
        assertEquals(null, s2.activeHintCell)
        // The next press starts a new highlight instead of revealing.
        val s3 = s2.hint()
        assertTrue(s3.hintCell != cell && s3.cells[s3.hintCell!!].value == 0)
    }

    @Test
    fun penaltyAddsTenPercentPerPoint() {
        val p = SudokuGenerator.generate(Difficulty.EASY, Random(2))
        // 2 mistakes (½ each = 1 point) + 1.5 hint points = 2.5 points = +25%.
        val s = GameState.fromPuzzle(p).copy(elapsedSeconds = 600, mistakes = 2, hintHalfPoints = 3)
        assertEquals(5, s.penaltyHalfPoints)
        assertEquals(150, s.penaltySeconds)
        assertEquals(750, s.finalSeconds)
        assertEquals("2½", formatPoints(s.penaltyHalfPoints))
    }

    @Test
    fun formatPointsUsesHalfSign() {
        assertEquals("0", formatPoints(0))
        assertEquals("½", formatPoints(1))
        assertEquals("1", formatPoints(2))
        assertEquals("1½", formatPoints(3))
        assertEquals("2½", formatPoints(5))
    }

    @Test
    fun penaltyFollowsTheSetting() {
        val p = SudokuGenerator.generate(Difficulty.EASY, Random(2))
        val s = GameState.fromPuzzle(p).copy(elapsedSeconds = 600, mistakes = 2, hintHalfPoints = 3)
        assertEquals(300, s.copy(penaltyPercent = 20).penaltySeconds)  // 2.5 points × 20% = 50%
        assertEquals(600, s.copy(penaltyPercent = 0).finalSeconds)
        assertEquals(50, s.copy(penaltyPercent = 20).penaltyTotalPercent)
    }

    @Test
    fun undoKeepsCellsRevealedByAHint() {
        val start = GameState.fromPuzzle(SudokuGenerator.generate(Difficulty.EASY, kotlin.random.Random(4)))
        val before = start.cells
        // Enter a correct digit, then reveal another cell with a two-step hint.
        val empty = before.indices.first { before[it].value == 0 }
        val entered = start.copy(selected = empty).enter(start.solution[empty])
        val revealed = entered.hint().hint()
        val hinted = revealed.cells.indices.first { revealed.cells[it].given && !before[it].given }
        val undone = revealed.restored(before)
        assertEquals(0, undone.cells[empty].value)
        assertEquals(start.solution[hinted], undone.cells[hinted].value)
        assertTrue(undone.cells[hinted].given)
        assertEquals(2, undone.hintHalfPoints)
    }

    @Test
    fun errorsSeenFollowsMovesWhileErrorsAreOn() {
        val p = SudokuGenerator.generate(Difficulty.EASY, Random(11))
        val empty = p.givens.indexOfFirst { it == 0 }
        val base = GameState.fromPuzzle(p).copy(selected = empty)
        assertFalse(base.errorsSeen)
        assertTrue(base.enter(p.solution[empty]).errorsSeen)
        // Errors on at the start but switched off before the first move: still unaided.
        var s = base.withShowWrong(false)
        assertFalse(s.errorsSeen)
        s = s.enter(p.solution[empty]).addNote(3).erase()
        assertFalse(s.errorsSeen)
        // Switching them on mid-game counts, and undo does not clear it.
        s = s.withShowWrong(true)
        assertTrue(s.errorsSeen)
        assertTrue(s.restored(base.cells).errorsSeen)
    }}
