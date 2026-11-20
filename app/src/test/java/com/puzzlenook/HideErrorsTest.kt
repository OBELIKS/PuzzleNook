package com.puzzlenook

import com.puzzlenook.hidato.HidatoGenerator
import com.puzzlenook.hidato.HidatoState
import com.puzzlenook.nonogram.Mark
import com.puzzlenook.nonogram.NonogramGenerator
import com.puzzlenook.nonogram.NonogramState
import com.puzzlenook.shade.ShadeState
import com.puzzlenook.shade.StarBattleGenerator
import com.puzzlenook.slither.SlitherGenerator
import com.puzzlenook.slither.SlitherState
import com.puzzlenook.sudoku.AnswerCheck
import com.puzzlenook.sudoku.Difficulty
import com.puzzlenook.sudoku.GameState
import com.puzzlenook.sudoku.SudokuGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** "Hide errors": no mistakes while hidden; wrong entries are charged once when shown again. */
class HideErrorsTest {
    private fun sudoku(): GameState = GameState.fromPuzzle(SudokuGenerator.generate(Difficulty.EASY, Random(1)))

    private fun GameState.enterAt(i: Int, d: Int) = copy(selected = i).enter(d)

    @Test
    fun sudokuWrongDigitsCostNothingWhileHidden() {
        var s = sudoku().withShowWrong(false)
        val i = s.cells.indices.first { !s.cells[it].given }
        val wrong = (1..9).first { it != s.solution[i] }
        s = s.enterAt(i, wrong)
        assertEquals(0, s.mistakes)
        assertFalse(s.shownWrong(i))
        assertTrue(s.isWrong(i))
        // Showing charges it once.
        s = s.withShowWrong(true)
        assertEquals(1, s.mistakes)
        assertTrue(s.shownWrong(i))
    }

    @Test
    fun fixedBeforeShowingCostsNothing() {
        var s = sudoku().withShowWrong(false)
        val i = s.cells.indices.first { !s.cells[it].given }
        s = s.enterAt(i, (1..9).first { it != s.solution[i] })
        s = s.enterAt(i, s.solution[i])
        assertEquals(0, s.withShowWrong(true).mistakes)
    }

    @Test
    fun alreadyChargedMistakeIsNotChargedAgain() {
        var s = sudoku()
        val i = s.cells.indices.first { !s.cells[it].given }
        s = s.enterAt(i, (1..9).first { it != s.solution[i] })
        assertEquals(1, s.mistakes)
        // Hide and show again with the same wrong digit still there.
        s = s.withShowWrong(false).withShowWrong(true)
        assertEquals(1, s.mistakes)
        // A second wrong cell made while hidden is charged on show, the first one is not.
        val j = s.cells.indices.first { !s.cells[it].given && it != i }
        s = s.withShowWrong(false).enterAt(j, (1..9).first { it != s.solution[j] }).withShowWrong(true)
        assertEquals(2, s.mistakes)
    }

    @Test
    fun hiddenModeClearsNotesForEveryDigit() {
        // While hidden, right and wrong digits both clear matching peer notes, so notes can't
        // tell them apart.
        for (right in listOf(true, false)) {
            var s = sudoku().withShowWrong(false)
            val i = s.cells.indices.first { !s.cells[it].given }
            val peer = s.cells.indices.first { it != i && !s.cells[it].given && s.isPeer(i, it) }
            val d = if (right) s.solution[i] else (1..9).first { it != s.solution[i] }
            s = s.copy(cells = s.cells.toMutableList().also { it[peer] = it[peer].copy(notes = 1 shl d) })
            s = s.enterAt(i, d)
            assertFalse("right=$right", s.cells[peer].hasNote(d))
        }
    }

    @Test
    fun shownModeKeepsNotesForWrongDigits() {
        var s = sudoku()
        val i = s.cells.indices.first { !s.cells[it].given }
        val peer = s.cells.indices.first { it != i && !s.cells[it].given && s.isPeer(i, it) }
        val d = (1..9).first { it != s.solution[i] }
        s = s.copy(cells = s.cells.toMutableList().also { it[peer] = it[peer].copy(notes = 1 shl d) })
        assertTrue(s.enterAt(i, d).cells[peer].hasNote(d))
    }

    @Test
    fun codeRoundTrip() {
        val m = mapOf(3 to 7, 12 to 1)
        assertEquals(m, AnswerCheck.decode(AnswerCheck.encode(m)))
        assertEquals(emptyMap<Int, Int>(), AnswerCheck.decode(""))
    }

    @Test
    fun nonogramHidden() {
        var s = NonogramState(
            difficulty = Difficulty.EASY, size = 2,
            solution = listOf(true, false, false, true),
            marks = List(4) { Mark.NONE }, fixed = List(4) { false },
        ).withShowWrong(false)
        s = s.set(1, Mark.FILLED)
        assertEquals(0, s.mistakes)
        assertFalse(s.shownWrong(1))
        s = s.withShowWrong(true)
        assertEquals(1, s.mistakes)
    }

    @Test
    fun slitherHidden() {
        val p = SlitherGenerator.generate(Difficulty.EASY, Random(2))
        var s = SlitherState.fromPuzzle(p).withShowWrong(false)
        val e = s.solution.indices.first { !s.solution[it] }
        s = s.set(e, Mark.FILLED)
        assertEquals(0, s.mistakes)
        assertEquals(1, s.withShowWrong(true).mistakes)
    }

    @Test
    fun hidatoHidden() {
        val p = HidatoGenerator.generate(Difficulty.EASY, Random(2))
        var s = HidatoState.fromPuzzle(p).withShowWrong(false)
        val i = s.values.indices.first { s.values[it] == 0 }
        val wrong = (1..s.max).first { it != s.solution[i] }
        s = s.enter(i, wrong)
        assertEquals(0, s.mistakes)
        assertTrue(wrong in (1..s.max).filter { it !in s.missing })
        assertEquals(1, s.withShowWrong(true).mistakes)
    }

    @Test
    fun starBattleHiddenGivesNoDotsAndNoMistakes() {
        var s = ShadeState.fromPuzzle(StarBattleGenerator.generate(Difficulty.EASY, Random(3))).withShowWrong(false)
        val right = s.solution.indices.first { s.solution[it] && !s.fixed[it] }
        val before = s.marks
        s = s.set(right, Mark.FILLED)
        // Only the star itself changed: no dots around it.
        assertEquals(1, s.marks.indices.count { s.marks[it] != before[it] })
        val wrong = s.solution.indices.first { !s.solution[it] && s.marks[it] == Mark.NONE }
        s = s.set(wrong, Mark.FILLED)
        assertEquals(0, s.mistakes)
        assertEquals(1, s.withShowWrong(true).mistakes)
    }
}
