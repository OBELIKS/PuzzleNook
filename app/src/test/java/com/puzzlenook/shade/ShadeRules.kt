package com.puzzlenook.shade

import com.puzzlenook.sudoku.Variant

/**
 * Plain rule checks and a naive search that share no code with the logic solvers; the independent brute force
 * for all shade games, Battleships and Aquarium included (Aquarium: levels per tank).
 */
object Rules {
    fun valid(p: ShadePuzzle, s: BooleanArray): Boolean {
        val n = p.size
        fun nb(i: Int) = listOfNotNull(
            (i - n).takeIf { it >= 0 }, (i + n).takeIf { it < n * n },
            (i - 1).takeIf { i % n > 0 }, (i + 1).takeIf { i % n < n - 1 },
        )
        fun connectedSet(cells: Set<Int>): Boolean {
            if (cells.isEmpty()) return true
            val seen = mutableSetOf(cells.first())
            val stack = ArrayDeque(listOf(cells.first()))
            while (stack.isNotEmpty()) for (j in nb(stack.removeLast())) if (j in cells && seen.add(j)) stack.addLast(j)
            return seen.size == cells.size
        }
        val shaded = (0 until n * n).filter { s[it] }.toSet()
        val white = (0 until n * n).filter { !s[it] }.toSet()
        return when (p.variant) {
            Variant.STAR_BATTLE -> {
                val k = p.stars
                (0 until n).all { r -> (0 until n).count { s[r * n + it] } == k } &&
                    (0 until n).all { c -> (0 until n).count { s[it * n + c] } == k } &&
                    (0 until n).all { g -> (0 until n * n).count { p.regions!![it] == g && s[it] } == k } &&
                    shaded.all { i -> shaded.none { j -> j != i && kotlin.math.abs(i / n - j / n) <= 1 && kotlin.math.abs(i % n - j % n) <= 1 } }
            }
            Variant.HITORI -> {
                val lines = (0 until n).map { r -> (0 until n).map { r * n + it } } + (0 until n).map { c -> (0 until n).map { it * n + c } }
                lines.all { l -> l.filter { !s[it] }.map { p.numbers[it] }.let { it.size == it.toSet().size } } &&
                    shaded.all { i -> nb(i).none { it in shaded } } &&
                    connectedSet(white) &&
                    // Only repeated numbers may be shaded.
                    shaded.all { i -> lines.any { l -> i in l && l.count { p.numbers[it] == p.numbers[i] } > 1 } }
            }
            Variant.NURIKABE -> {
                val no2x2 = (0 until n - 1).all { r -> (0 until n - 1).all { c ->
                    !(s[r * n + c] && s[r * n + c + 1] && s[(r + 1) * n + c] && s[(r + 1) * n + c + 1])
                } }
                val seen = mutableSetOf<Int>()
                val islandsOk = white.all { start ->
                    if (start in seen) return@all true
                    val isl = mutableSetOf(start)
                    val stack = ArrayDeque(listOf(start))
                    while (stack.isNotEmpty()) for (j in nb(stack.removeLast())) if (j in white && isl.add(j)) stack.addLast(j)
                    seen += isl
                    val clues = isl.filter { p.numbers[it] > 0 }
                    clues.size == 1 && p.numbers[clues[0]] == isl.size
                }
                no2x2 && islandsOk && connectedSet(shaded) && (0 until n * n).all { p.numbers[it] == 0 || !s[it] }
            }
            Variant.TAKUZU -> {
                val lines = (0 until n).map { r -> (0 until n).map { r * n + it } } + (0 until n).map { c -> (0 until n).map { it * n + c } }
                lines.all { l ->
                    l.count { s[it] } == n / 2 &&
                        (0 until n - 2).none { k -> s[l[k]] == s[l[k + 1]] && s[l[k]] == s[l[k + 2]] }
                } &&
                    lines.take(n).map { l -> l.map { s[it] } }.toSet().size == n &&
                    lines.drop(n).map { l -> l.map { s[it] } }.toSet().size == n
            }
            Variant.MOSAIC -> (0 until n * n).all { c ->
                p.numbers[c] < 0 || (listOf(c) + nb(c) + diag(c, n)).count { s[it] } == p.numbers[c]
            }
            Variant.LIGHT_UP -> {
                fun wall(i: Int) = p.numbers[i] >= 0
                // Walk from i in one direction until a wall or the edge; true if a bulb is met.
                fun seesBulb(i: Int): Boolean {
                    for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                        var r = i / n + dr
                        var c = i % n + dc
                        while (r in 0 until n && c in 0 until n && !wall(r * n + c)) {
                            if (s[r * n + c]) return true
                            r += dr
                            c += dc
                        }
                    }
                    return false
                }
                (0 until n * n).all { i ->
                    when {
                        wall(i) -> !s[i] && (p.numbers[i] == WALL || nb(i).count { s[it] } == p.numbers[i])
                        s[i] -> !seesBulb(i)
                        else -> seesBulb(i)
                    }
                }
            }
            Variant.TENTS -> {
                val trees = (0 until n * n).filter { p.numbers[it] == TREE }
                val tents = shaded.toList()
                val counts = p.counts!!
                // Brute-force matching: give each tree its own adjacent tent, trying every choice.
                fun assign(k: Int, used: Set<Int>): Boolean =
                    k == trees.size || nb(trees[k]).any { it in shaded && it !in used && assign(k + 1, used + it) }
                tents.none { p.numbers[it] == TREE } &&
                    tents.size == trees.size &&
                    tents.all { a -> tents.none { b -> b != a && kotlin.math.abs(a / n - b / n) <= 1 && kotlin.math.abs(a % n - b % n) <= 1 } } &&
                    (0 until n).all { r -> counts[r] < 0 || tents.count { it / n == r } == counts[r] } &&
                    (0 until n).all { c -> counts[n + c] < 0 || tents.count { it % n == c } == counts[n + c] } &&
                    assign(0, emptySet())
            }
            Variant.KURODOKO -> {
                // Walk each of the four directions from a clue, counting cells until a shaded one.
                fun seen(c: Int): Int {
                    var total = 1
                    for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                        var r = c / n + dr
                        var k = c % n + dc
                        while (r in 0 until n && k in 0 until n && !s[r * n + k]) { total++; r += dr; k += dc }
                    }
                    return total
                }
                (0 until n * n).all { i -> p.numbers[i] <= 0 || (!s[i] && seen(i) == p.numbers[i]) } &&
                    shaded.all { i -> nb(i).none { it in shaded } } &&
                    connectedSet(white)
            }
            Variant.NORINORI -> {
                val g = p.regions!!
                (g.max() + 1).let { regionCount -> (0 until regionCount).all { r -> shaded.count { g[it] == r } == 2 } } &&
                    shaded.all { i -> nb(i).count { it in shaded } == 1 }
            }
            Variant.BATTLESHIPS -> {
                val counts = p.counts!!
                fun at(r: Int, c: Int) = r in 0 until n && c in 0 until n && s[r * n + c]
                fun shape(num: Int, i: Int): Boolean {
                    val r = i / n
                    val c = i % n
                    if (num == 0) return !s[i]
                    val up = at(r - 1, c)
                    val down = at(r + 1, c)
                    val left = at(r, c - 1)
                    val right = at(r, c + 1)
                    return s[i] && when (num) {
                        1 -> !up && !down && !left && !right
                        2 -> down && !up && !left && !right
                        3 -> up && !down && !left && !right
                        4 -> right && !up && !down && !left
                        5 -> left && !up && !down && !right
                        else -> (up && down && !left && !right) || (left && right && !up && !down)
                    }
                }
                // Ships: groups of shaded cells that touch in any of the eight directions must be straight runs.
                val seen = mutableSetOf<Int>()
                val lengths = ArrayList<Int>()
                var straight = true
                for (start in shaded) {
                    if (start in seen) continue
                    val group = mutableSetOf(start)
                    val stack = ArrayDeque(listOf(start))
                    while (stack.isNotEmpty()) {
                        val x = stack.removeLast()
                        for (dr in -1..1) for (dc in -1..1) {
                            val y = (x / n + dr) * n + x % n + dc
                            if (at(x / n + dr, x % n + dc) && group.add(y)) stack.addLast(y)
                        }
                    }
                    seen += group
                    val rows = group.map { it / n }.toSet()
                    val cols = group.map { it % n }.toSet()
                    val line = (rows.size == 1 && cols.max() - cols.min() + 1 == group.size) || (cols.size == 1 && rows.max() - rows.min() + 1 == group.size)
                    if (!line) straight = false
                    lengths += group.size
                }
                straight && lengths.sorted() == BattleshipsGenerator.fleet(n).sorted() &&
                    (0 until n).all { r -> (0 until n).count { s[r * n + it] } == counts[r] } &&
                    (0 until n).all { c -> (0 until n).count { s[it * n + c] } == counts[n + c] } &&
                    (0 until n * n).all { p.numbers[it] < 0 || shape(p.numbers[it], it) }
            }
            Variant.AQUARIUM -> {
                val g = p.regions!!
                val counts = p.counts!!
                // Every tank: some water level makes each of its cells water exactly when it lies at or below it.
                (0..g.max()).all { t ->
                    val tc = (0 until n * n).filter { g[it] == t }
                    (tc.minOf { it / n }..tc.maxOf { it / n } + 1).any { level -> tc.all { s[it] == (it / n >= level) } }
                } &&
                    (0 until n).all { r -> counts[r] < 0 || (0 until n).count { s[r * n + it] } == counts[r] } &&
                    (0 until n).all { c -> counts[n + c] < 0 || (0 until n).count { s[it * n + c] } == counts[n + c] }
            }
            Variant.HEYAWAKE -> {
                val g = p.regions!!
                // Maximal runs of white cells in every row and column cross at most two rooms.
                val lines = (0 until n).map { r -> (0 until n).map { r * n + it } } + (0 until n).map { c -> (0 until n).map { it * n + c } }
                val runsOk = lines.all { l ->
                    val runs = ArrayList<MutableList<Int>>()
                    var cur = ArrayList<Int>()
                    for (x in l) if (s[x]) { runs += cur; cur = ArrayList() } else cur.add(x)
                    runs += cur
                    runs.all { run -> run.map { g[it] }.toSet().size <= 2 }
                }
                (0 until n * n).all { i -> p.numbers[i] < 0 || shaded.count { g[it] == g[i] } == p.numbers[i] } &&
                    shaded.all { i -> nb(i).none { it in shaded } } &&
                    connectedSet(white) && runsOk
            }
            Variant.LITS -> {
                val g = p.regions!!
                val byRegion = (0..g.max()).map { r -> shaded.filter { g[it] == r } }
                // Shape name: the smallest sorted coordinate list over the eight symmetries.
                fun canon(cells: List<Int>): String = (0 until 8).map { t ->
                    val pts = cells.map { c ->
                        var r = c / n
                        var k = c % n
                        if (t and 1 != 0) { val x = r; r = k; k = x }
                        if (t and 2 != 0) r = -r
                        if (t and 4 != 0) k = -k
                        r to k
                    }
                    val r0 = pts.minOf { it.first }
                    val k0 = pts.minOf { it.second }
                    pts.map { (it.first - r0) * 10 + (it.second - k0) }.sorted().toString()
                }.min()
                val noSquare = (0 until n - 1).all { r -> (0 until n - 1).all { c ->
                    !(s[r * n + c] && s[r * n + c + 1] && s[(r + 1) * n + c] && s[(r + 1) * n + c + 1])
                } }
                val shapes = byRegion.map { if (it.size == 4) canon(it) else "" }
                byRegion.all { it.size == 4 && connectedSet(it.toSet()) } && noSquare && connectedSet(shaded) &&
                    shaded.all { i -> nb(i).none { j -> j in shaded && g[j] != g[i] && shapes[g[i]] == shapes[g[j]] } }
            }
            else -> error("${p.variant}")
        }
    }

    /**
     * Heyawake prune after deciding cell [i] (cells 0..i are set): shaded cells never touch (left and
     * above), a numbered room keeps a way to its number, and a run of white cells that ends at [i] (at a
     * shaded cell or the edge) spans at most two rooms.
     */
    private fun heyawakeOk(p: ShadePuzzle, s: BooleanArray, i: Int, v: Boolean, cellsOf: List<List<Int>>, target: IntArray): Boolean {
        val n = p.size
        val r = i / n
        val c = i % n
        val g = p.regions!!
        if (v && ((c > 0 && s[i - 1]) || (r > 0 && s[i - n]))) return false
        val t = target[g[i]]
        if (t >= 0) {
            val room = cellsOf[g[i]]
            val sofar = room.count { it <= i && s[it] }
            if (sofar > t || sofar + room.count { it > i } < t) return false
        }
        if (v || c == n - 1) {
            val rooms = HashSet<Int>()
            var k = if (v) c - 1 else c
            while (k >= 0 && !s[r * n + k]) { rooms += g[r * n + k]; k-- }
            if (rooms.size >= 3) return false
        }
        if (v || r == n - 1) {
            val rooms = HashSet<Int>()
            var k = if (v) r - 1 else r
            while (k >= 0 && !s[k * n + c]) { rooms += g[k * n + c]; k-- }
            if (rooms.size >= 3) return false
        }
        return true
    }

    /** LITS: every region takes each of its connected four-cell subsets in turn; [valid] checks the rest. Shares nothing with the logic. */
    private fun countLits(p: ShadePuzzle, limit: Int): Int {
        val n = p.size
        val g = p.regions!!
        val regs = (0..g.max()).map { r -> g.indices.filter { g[it] == r } }
        fun orthTouch(a: Int, b: Int) = kotlin.math.abs(a / n - b / n) + kotlin.math.abs(a % n - b % n) == 1
        fun linked(cells: List<Int>): Boolean {
            val seen = mutableSetOf(cells[0])
            val stack = ArrayDeque(listOf(cells[0]))
            while (stack.isNotEmpty()) { val x = stack.removeLast(); for (y in cells) if (orthTouch(x, y) && seen.add(y)) stack.addLast(y) }
            return seen.size == 4
        }
        fun canon(cells: List<Int>): String = (0 until 8).map { t ->
            val pts = cells.map { c ->
                var r = c / n
                var k = c % n
                if (t and 1 != 0) { val x = r; r = k; k = x }
                if (t and 2 != 0) r = -r
                if (t and 4 != 0) k = -k
                r to k
            }
            val r0 = pts.minOf { it.first }
            val k0 = pts.minOf { it.second }
            pts.map { (it.first - r0) * 10 + (it.second - k0) }.sorted().toString()
        }.min()
        val options = regs.map { cells ->
            val out = ArrayList<List<Int>>()
            for (a in cells.indices) for (b in a + 1 until cells.size) for (c in b + 1 until cells.size) for (d in c + 1 until cells.size) {
                val q = listOf(cells[a], cells[b], cells[c], cells[d])
                if (!linked(q)) continue
                if (q.maxOf { it / n } - q.minOf { it / n } == 1 && q.maxOf { it % n } - q.minOf { it % n } == 1) continue
                if (cells.any { x -> p.givens[x] && (x in q) != p.solution[x] }) continue
                out += q
            }
            out
        }
        val s = BooleanArray(n * n)
        val shapes = Array(regs.size) { "" }
        var found = 0
        fun rec(k: Int) {
            if (found >= limit) return
            if (k == regs.size) {
                if (valid(p, s)) found++
                return
            }
            for (opt in options[k]) {
                for (x in opt) s[x] = true
                val shape = canon(opt)
                val square = opt.any { x ->
                    (-1..0).any { dr -> (-1..0).any { dc ->
                        val r = x / n + dr
                        val c = x % n + dc
                        r >= 0 && c >= 0 && r + 1 < n && c + 1 < n && s[r * n + c] && s[r * n + c + 1] && s[(r + 1) * n + c] && s[(r + 1) * n + c + 1]
                    } }
                }
                val clash = opt.any { x -> (0 until n * n).any { y -> s[y] && g[y] != k && orthTouch(x, y) && shapes[g[y]] == shape } }
                if (!square && !clash) {
                    shapes[k] = shape
                    rec(k + 1)
                    shapes[k] = ""
                }
                for (x in opt) s[x] = false
            }
        }
        rec(0)
        return found
    }

    /**
     * Battleships prune after deciding cell [i] (cells 0..i are set): shown cells keep their state, no ship
     * cell touches an earlier one diagonally or bends, no run is longer than the longest ship, counts are
     * never exceeded and can still be met.
     */
    private fun battleshipsOk(p: ShadePuzzle, s: BooleanArray, i: Int, v: Boolean): Boolean {
        val n = p.size
        val r = i / n
        val c = i % n
        val counts = p.counts!!
        if (p.numbers[i] >= 0 && v != (p.numbers[i] > 0)) return false
        if (v) {
            if (r > 0 && c > 0 && s[i - n - 1]) return false
            if (r > 0 && c < n - 1 && s[i - n + 1]) return false
            if (r > 0 && c > 0 && s[i - n] && s[i - 1]) return false
            val longest = BattleshipsGenerator.fleet(n).max()
            var run = 1
            var k = c - 1
            while (k >= 0 && s[r * n + k]) { run++; k-- }
            if (run > longest) return false
            run = 1
            k = r - 1
            while (k >= 0 && s[k * n + c]) { run++; k-- }
            if (run > longest) return false
        }
        val inRow = (0..c).count { s[r * n + it] }
        val inCol = (0..r).count { s[it * n + c] }
        if (counts[r] >= 0 && (inRow > counts[r] || inRow + (n - 1 - c) < counts[r])) return false
        if (counts[n + c] >= 0 && (inCol > counts[n + c] || inCol + (n - 1 - r) < counts[n + c])) return false
        return true
    }

    /** Aquarium: every tank gets a level in turn; row and column counts prune. Shares nothing with the logic. */
    private fun countAquarium(p: ShadePuzzle, limit: Int): Int {
        val n = p.size
        val g = p.regions!!
        val counts = p.counts!!
        val tanks = (0..g.max()).map { t -> (0 until n * n).filter { g[it] == t } }
        val rowSum = IntArray(n)
        val colSum = IntArray(n)
        var found = 0
        fun rec(t: Int) {
            if (found >= limit) return
            if (t == tanks.size) {
                if ((0 until n).all { counts[it] < 0 || rowSum[it] == counts[it] } && (0 until n).all { counts[n + it] < 0 || colSum[it] == counts[n + it] }) found++
                return
            }
            val tc = tanks[t]
            for (level in tc.minOf { it / n }..tc.maxOf { it / n } + 1) {
                if (tc.any { p.givens[it] && (it / n >= level) != p.solution[it] }) continue
                val water = tc.filter { it / n >= level }
                for (x in water) { rowSum[x / n]++; colSum[x % n]++ }
                if ((0 until n).all { counts[it] < 0 || rowSum[it] <= counts[it] } && (0 until n).all { counts[n + it] < 0 || colSum[it] <= counts[n + it] }) rec(t + 1)
                for (x in water) { rowSum[x / n]--; colSum[x % n]-- }
            }
        }
        rec(0)
        return found
    }

    /**
     * Norinori prune after deciding cell [i] (cells 0..i are set): a region never holds more than two
     * shaded cells nor too few places left for two, no shaded cell has two shaded neighbours, and the
     * cell above [i] (now fully decided) has exactly one shaded neighbour when it is shaded.
     */
    private fun norinoriOk(p: ShadePuzzle, s: BooleanArray, i: Int, cellsOf: List<List<Int>>): Boolean {
        val n = p.size
        val r = i / n
        val c = i % n
        val region = cellsOf[p.regions!![i]]
        val sofar = region.count { it <= i && s[it] }
        if (sofar > 2 || sofar + region.count { it > i } < 2) return false
        fun at(rr: Int, cc: Int) = rr in 0 until n && cc in 0 until n && s[rr * n + cc] && rr * n + cc <= i
        fun around(rr: Int, cc: Int) = listOf(rr - 1 to cc, rr + 1 to cc, rr to cc - 1, rr to cc + 1).count { (a, b) -> at(a, b) }
        if (s[i] && around(r, c) > 1) return false
        if (c > 0 && s[i - 1] && around(r, c - 1) > 1) return false
        if (r > 0 && s[i - n] && around(r - 1, c) != 1) return false
        return true
    }

    private fun diag(i: Int, n: Int) = listOf(-1, 1).flatMap { dr -> listOf(-1, 1).map { dc -> i / n + dr to i % n + dc } }
        .filter { (r, c) -> r in 0 until n && c in 0 until n }.map { (r, c) -> r * n + c }

    /**
     * Light Up prune after deciding cell [i] (cells 0..i are set): no bulb on a wall, no two bulbs
     * in sight, numbered walls still reachable, and every cell whose whole line of sight is decided
     * is lit.
     */
    private fun lightUpOk(p: ShadePuzzle, s: BooleanArray, i: Int, v: Boolean): Boolean {
        val n = p.size
        val r = i / n
        val c = i % n
        fun wall(j: Int) = p.numbers[j] >= 0
        if (wall(i)) return !v
        if (v) {
            // Earlier bulbs to the left and above, up to a wall.
            var cc = c - 1
            while (cc >= 0 && !wall(r * n + cc)) { if (s[r * n + cc]) return false; cc-- }
            var rr = r - 1
            while (rr >= 0 && !wall(rr * n + c)) { if (s[rr * n + c]) return false; rr-- }
        }
        // Numbered walls next to cells up to i: at most their number, and still reachable.
        for (w in 0 until n * n) {
            val k = p.numbers[w]
            if (k !in 0..4 || kotlin.math.abs(w / n - r) + kotlin.math.abs(w % n - c) > 2) continue
            val cells = listOfNotNull(
                (w - n).takeIf { w >= n && !wall(it) }, (w + n).takeIf { w + n < n * n && !wall(it) },
                (w - 1).takeIf { w % n > 0 && !wall(it) }, (w + 1).takeIf { w % n < n - 1 && !wall(it) },
            )
            val sofar = cells.count { it <= i && s[it] }
            if (sofar > k || sofar + cells.count { it > i } < k) return false
        }
        // Cells whose line of sight ends at i or earlier must be lit by now.
        val recent = mutableListOf(i)
        var cc2 = c - 1
        while (cc2 >= 0 && !wall(r * n + cc2)) { recent += r * n + cc2; cc2-- }
        var rr2 = r - 1
        while (rr2 >= 0 && !wall(rr2 * n + c)) { recent += rr2 * n + c; rr2-- }
        for (j in recent) {
            if (wall(j) || s[j]) continue
            var lastCell = j
            var lit = false
            for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                var y = j / n + dr
                var x = j % n + dc
                while (y in 0 until n && x in 0 until n && !wall(y * n + x)) {
                    val k = y * n + x
                    if (k > lastCell) lastCell = k
                    if (k <= i && s[k]) lit = true
                    y += dr
                    x += dc
                }
            }
            if (lastCell <= i && !lit) return false
        }
        return true
    }

    /**
     * Tents prune after deciding cell [i] (cells 0..i are set): tents only on free cells beside a
     * tree, none touching an earlier tent, visible counts never exceeded and met once a line is done.
     */
    private fun tentsOk(p: ShadePuzzle, s: BooleanArray, i: Int, v: Boolean): Boolean {
        val n = p.size
        val r = i / n
        val c = i % n
        val counts = p.counts!!
        if (v) {
            if (p.numbers[i] == TREE) return false
            val nextToTree = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).any { (dr, dc) ->
                r + dr in 0 until n && c + dc in 0 until n && p.numbers[(r + dr) * n + c + dc] == TREE
            }
            if (!nextToTree) return false
            if (r > 0 && (((c - 1)..(c + 1)).any { it in 0 until n && s[(r - 1) * n + it] })) return false
            if (c > 0 && s[i - 1]) return false
        }
        val inRow = (0..c).count { s[r * n + it] }
        val inCol = (0..r).count { s[it * n + c] }
        if (counts[r] >= 0 && (inRow > counts[r] || (c == n - 1 && inRow != counts[r]))) return false
        if (counts[n + c] >= 0 && (inCol > counts[n + c] || (r == n - 1 && inCol != counts[n + c]))) return false
        return true
    }

    /**
     * Kurodoko prune after deciding cell [i] (cells 0..i are set): no shaded clue, no shaded cell
     * beside an earlier one, and every clue in the row or column of [i] can still reach its number
     * (cells not yet decided count as white for the most it can see, as shaded for the least).
     */
    private fun kurodokoOk(p: ShadePuzzle, s: BooleanArray, i: Int, v: Boolean): Boolean {
        val n = p.size
        val r = i / n
        val c = i % n
        if (v) {
            if (p.numbers[i] > 0) return false
            if (r > 0 && s[i - n]) return false
            if (c > 0 && s[i - 1]) return false
        }
        for (clue in 0 until n * n) {
            val k = p.numbers[clue]
            if (k <= 0 || (clue / n != r && clue % n != c)) continue
            var lo = 1
            var hi = 1
            for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                var y = clue / n + dr
                var x = clue % n + dc
                var sure = true
                while (y in 0 until n && x in 0 until n) {
                    val j = y * n + x
                    val decided = j <= i
                    if (decided && s[j]) break
                    hi++
                    if (sure && decided) lo++ else sure = false
                    y += dr
                    x += dc
                }
            }
            if (lo > k || hi < k) return false
        }
        return true
    }

    /** Cell-by-cell search with a few cheap prunes, then [valid] on every full grid. */
    fun countSolutions(p: ShadePuzzle, limit: Int): Int {
        if (p.variant == Variant.AQUARIUM) return countAquarium(p, limit)
        if (p.variant == Variant.LITS) return countLits(p, limit)
        val n = p.size
        val s = BooleanArray(n * n)
        var found = 0
        val regionCells = p.regions?.let { g -> (0..g.max()).map { r -> g.indices.filter { g[it] == r } } }
        val roomTarget = IntArray(regionCells?.size ?: 0) { -1 }
        if (p.variant == Variant.HEYAWAKE) for (i in 0 until n * n) if (p.numbers[i] >= 0) roomTarget[p.regions!![i]] = p.numbers[i]
        fun rec(i: Int) {
            if (found >= limit) return
            if (i == n * n) {
                if (valid(p, s)) found++
                return
            }
            val r = i / n
            val c = i % n
            for (v in listOf(false, true)) {
                if (p.givens[i] && v != p.solution[i]) continue
                s[i] = v
                val ok = when (p.variant) {
                    Variant.TAKUZU -> (c < 2 || !(s[i - 1] == v && s[i - 2] == v)) &&
                        (r < 2 || !(s[i - n] == v && s[i - 2 * n] == v)) &&
                        (0..c).count { s[r * n + it] == v } <= n / 2 &&
                        (0..r).count { s[it * n + c] == v } <= n / 2
                    Variant.STAR_BATTLE -> !v || (
                        (r == 0 || ((c - 1)..(c + 1)).none { cc -> cc in 0 until n && s[(r - 1) * n + cc] }) &&
                            (c == 0 || !s[i - 1]) &&
                            (0 until c).count { s[r * n + it] } < p.stars &&
                            (0 until r).count { s[it * n + c] } < p.stars
                        )
                    Variant.HITORI -> (!v || ((r == 0 || !s[i - n]) && (c == 0 || !s[i - 1]))) &&
                        (v || ((0 until c).none { !s[r * n + it] && p.numbers[r * n + it] == p.numbers[i] } &&
                            (0 until r).none { !s[it * n + c] && p.numbers[it * n + c] == p.numbers[i] }))
                    Variant.NURIKABE -> (!v || p.numbers[i] == 0) &&
                        (!v || r == 0 || c == 0 || !(s[i - 1] && s[i - n] && s[i - n - 1]))
                    Variant.MOSAIC -> (0 until n * n).all { cl ->
                        val num = p.numbers[cl]
                        if (num < 0 || kotlin.math.abs(cl / n - r) > 1 || kotlin.math.abs(cl % n - c) > 1) return@all true
                        val block = (-1..1).flatMap { dr -> (-1..1).map { dc -> (cl / n + dr) to (cl % n + dc) } }
                            .filter { (br, bc) -> br in 0 until n && bc in 0 until n }.map { (br, bc) -> br * n + bc }
                        val sofar = block.count { it <= i && s[it] }
                        sofar <= num && sofar + block.count { it > i } >= num
                    }
                    Variant.LIGHT_UP -> lightUpOk(p, s, i, v)
                    Variant.TENTS -> tentsOk(p, s, i, v)
                    Variant.KURODOKO -> kurodokoOk(p, s, i, v)
                    Variant.NORINORI -> norinoriOk(p, s, i, regionCells!!)
                    Variant.BATTLESHIPS -> battleshipsOk(p, s, i, v)
                    Variant.HEYAWAKE -> heyawakeOk(p, s, i, v, regionCells!!, roomTarget)
                    else -> true
                }
                if (ok) rec(i + 1)
            }
            s[i] = false
        }
        rec(0)
        return found
    }
}
