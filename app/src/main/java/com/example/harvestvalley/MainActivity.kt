package com.example.harvestvalley

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.random.Random

class MainActivity : Activity() {

    private data class Crop(val kind: String, var growth: Int, var watered: Boolean)
    private data class Tile(var tilled: Boolean = false, var crop: Crop? = null)
    private data class Animal(
        var kind: String,
        var name: String,
        var friendship: Int = 0,
        var fed: Boolean = false,
        var product: Boolean = false
    )
    private data class Npc(
        var name: String,
        var x: Int,
        var y: Int,
        var color: Int,
        var favorite: String,
        var friendship: Int = 0,
        var talkedToday: Boolean = false,
        var spouse: Boolean = false
    )
    private data class CropInfo(
        val seed: String,
        val produce: String,
        val days: Int,
        val price: Int,
        val season: Int,
        val label: String
    )

    companion object {
        private const val PREFS = "harvest_valley_prefs"
        private const val SAVE_KEY = "save"
        private const val W = 12
        private const val H = 10
        private const val FARM_X1 = 1
        private const val FARM_X2 = 6
        private const val FARM_Y1 = 3
        private const val FARM_Y2 = 7
    }

    private val seasons = arrayOf("Semi", "Panas", "Gugur", "Dingin")
    private val weathers = arrayOf("Cerah", "Berawan", "Hujan")
    private val crops = linkedMapOf(
        "turnip" to CropInfo("turnip_seed", "turnip", 3, 60, 0, "Lobak"),
        "tomato" to CropInfo("tomato_seed", "tomato", 5, 90, 1, "Tomat"),
        "pumpkin" to CropInfo("pumpkin_seed", "pumpkin", 6, 140, 2, "Labu"),
        "wheat" to CropInfo("wheat_seed", "wheat", 4, 80, 3, "Gandum")
    )
    private val productPrices = mapOf("egg" to 80, "milk" to 120)
    private val itemNames = mapOf(
        "turnip_seed" to "Benih Lobak",
        "tomato_seed" to "Benih Tomat",
        "pumpkin_seed" to "Benih Labu",
        "wheat_seed" to "Benih Gandum",
        "turnip" to "Lobak",
        "tomato" to "Tomat",
        "pumpkin" to "Labu",
        "wheat" to "Gandum",
        "egg" to "Telur",
        "milk" to "Susu",
        "feed" to "Pakan"
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var gameView: GameView

    private var year = 1
    private var season = 0
    private var day = 1
    private var minutes = 6 * 60
    private var stamina = 100
    private var gold = 500
    private var weather = "Cerah"
    private var playerX = 4
    private var playerY = 2
    private var selectedSeed = "turnip"
    private var spouseName = ""
    private var tiles = Array(H) { Array(W) { Tile() } }
    private var inventory = mutableMapOf<String, Int>()
    private var animals = mutableListOf<Animal>()
    private var npcs = mutableListOf<Npc>()
    private var log = mutableListOf<String>()
    private val walkHandler = Handler(Looper.getMainLooper())
    private var walking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        setContentView(R.layout.activity_main)
        loadGame()
        gameView = GameView(this)
        gameView.isClickable = true
        gameView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        findViewById<FrameLayout>(R.id.gameRoot).addView(gameView, 0)
        bindControls()
        redraw()
    }

    private fun bindControls() {
        findViewById<Button>(R.id.btnAction).setOnClickListener { action() }
        findViewById<Button>(R.id.btnSleep).setOnClickListener { confirmSleep() }
        findViewById<Button>(R.id.btnMenu).setOnClickListener { openMenu() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun redraw() {
        if (::gameView.isInitialized) gameView.invalidate()
    }

    private fun resetState() {
        stopWalking()
        year = 1
        season = 0
        day = 1
        minutes = 6 * 60
        stamina = 100
        gold = 500
        weather = "Cerah"
        playerX = 4
        playerY = 2
        selectedSeed = "turnip"
        spouseName = ""
        tiles = Array(H) { Array(W) { Tile() } }
        inventory = mutableMapOf(
            "turnip_seed" to 8,
            "tomato_seed" to 4,
            "pumpkin_seed" to 3,
            "wheat_seed" to 4,
            "feed" to 2
        )
        animals = mutableListOf()
        npcs = mutableListOf(
            Npc("Aira", 8, 6, Color.parseColor("#EF6C00"), "turnip"),
            Npc("Bima", 10, 6, Color.parseColor("#1976D2"), "egg"),
            Npc("Sari", 9, 8, Color.parseColor("#C2185B"), "pumpkin")
        )
        log = mutableListOf("Ketuk tanah untuk berjalan, lalu tekan AKSI.")
    }

    private fun seedForSeason(): String = when (season) {
        0 -> "turnip"
        1 -> "tomato"
        2 -> "pumpkin"
        else -> "wheat"
    }

    private fun isFestival(): Boolean = day == 7

    private fun itemLabel(key: String): String = itemNames[key] ?: key.replace('_', ' ')

    private fun addLog(message: String) {
        log.add(0, message)
        if (log.size > 3) log.removeAt(log.size - 1)
        redraw()
    }

    private fun advance(mins: Int, cost: Int) {
        minutes += mins
        stamina -= cost
        if (stamina <= 0 || minutes >= 22 * 60) collapse()
        redraw()
    }

    private fun collapse() {
        addLog("Kamu kelelahan dan dibawa pulang.")
        nextDay(55)
    }

    private fun confirmSleep() {
        if (!(playerX == 1 && playerY == 2)) {
            toast("Tidur di rumah, pintu di kiri atas.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Istirahat")
            .setMessage("Lanjut ke hari berikutnya?")
            .setPositiveButton("Tidur") { _, _ -> nextDay(100) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun nextDay(newStamina: Int) {
        stopWalking()
        day++
        if (day > 7) {
            day = 1
            season++
            if (season > 3) {
                season = 0
                year++
            }
            selectedSeed = seedForSeason()
        }
        weather = weathers[Random.nextInt(weathers.size)]
        for (row in tiles) {
            for (tile in row) {
                val crop = tile.crop
                if (crop != null) {
                    if (crop.watered) crop.growth++
                    crop.watered = weather == "Hujan"
                }
            }
        }
        for (animal in animals) {
            if (animal.fed) animal.friendship = (animal.friendship + 1).coerceAtMost(10)
            else animal.friendship = (animal.friendship - 1).coerceAtLeast(0)
            animal.fed = false
            animal.product = true
        }
        for (npc in npcs) npc.talkedToday = false
        stamina = newStamina
        minutes = 6 * 60
        playerX = 4
        playerY = 2
        addLog("Hari $day ${seasons[season]}, cuaca $weather.")
        saveGame()
    }

    private fun blocked(x: Int, y: Int): Boolean {
        if (x < 0 || x >= W || y < 0 || y >= H) return true
        if (x in 0..2 && y in 0..2 && !(x == 1 && y == 2)) return true
        if (x in 9..11 && y in 0..2 && !(x == 10 && y == 2)) return true
        if (x in 0..2 && y in 8..9 && !(x == 1 && y == 8)) return true
        return false
    }

    private fun move(dx: Int, dy: Int) {
        val nx = playerX + dx
        val ny = playerY + dy
        if (blocked(nx, ny)) return
        playerX = nx
        playerY = ny
        minutes += 5
        redraw()
    }

    private fun stopWalking() {
        walking = false
        walkHandler.removeCallbacksAndMessages(null)
    }

    private fun startWalk(tx: Int, ty: Int) {
        if (blocked(tx, ty)) return
        stopWalking()
        val path = findPath(playerX, playerY, tx, ty)
        if (path.isEmpty()) return
        walking = true
        walkStep(path, 0)
    }

    private fun walkStep(path: List<Pair<Int, Int>>, index: Int) {
        if (!walking || index >= path.size) {
            walking = false
            return
        }
        val (nx, ny) = path[index]
        move(nx - playerX, ny - playerY)
        walkHandler.postDelayed({ walkStep(path, index + 1) }, 110)
    }

    private fun findPath(sx: Int, sy: Int, tx: Int, ty: Int): List<Pair<Int, Int>> {
        val visited = Array(H) { BooleanArray(W) }
        val previous = Array(H) { arrayOfNulls<Pair<Int, Int>>(W) }
        val queue: java.util.ArrayDeque<Pair<Int, Int>> = java.util.ArrayDeque()
        visited[sy][sx] = true
        queue.add(sx to sy)
        val dirs = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

        while (!queue.isEmpty()) {
            val (x, y) = queue.removeFirst()
            if (x == tx && y == ty) break
            for ((dx, dy) in dirs) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until W && ny in 0 until H &&
                    !blocked(nx, ny) && !visited[ny][nx]
                ) {
                    visited[ny][nx] = true
                    previous[ny][nx] = x to y
                    queue.add(nx to ny)
                }
            }
        }

        if (!visited[ty][tx]) return emptyList()
        val path = mutableListOf<Pair<Int, Int>>()
        var current: Pair<Int, Int>? = tx to ty
        while (current != null && current != (sx to sy)) {
            path.add(0, current)
            current = previous[current.second][current.first]
        }
        return path
    }

    private fun action() {
        when {
            playerX == 1 && playerY == 2 -> confirmSleep()
            playerX == 10 && playerY == 2 -> openShop()
            playerX == 1 && playerY == 8 -> openBarn()
            playerX == 7 && playerY == 3 -> shipAll()
            isFestival() && playerX == 10 && playerY == 5 -> openFestival()
            else -> {
                val npc = npcs.find { it.x == playerX && it.y == playerY }
                if (npc != null) {
                    talkNpc(npc)
                } else if (playerX in FARM_X1..FARM_X2 && playerY in FARM_Y1..FARM_Y2) {
                    farmAction(playerX, playerY)
                } else {
                    addLog("Tidak ada yang bisa dilakukan di sini.")
                }
            }
        }
    }

    private fun farmAction(x: Int, y: Int) {
        val tile = tiles[y][x]
        val crop = tile.crop
        if (crop != null) {
            val info = crops[crop.kind]!!
            if (crop.growth >= info.days) {
                inventory[info.produce] = (inventory[info.produce] ?: 0) + 1
                tile.crop = null
                addLog("Memanen ${info.label}!")
                advance(10, 4)
                saveGame()
                return
            }
            if (!crop.watered) {
                crop.watered = true
                addLog("Menyiram ${info.label}.")
                advance(5, 2)
                saveGame()
                return
            }
            addLog("${info.label} tumbuh: ${crop.growth}/${info.days} hari.")
            advance(5, 0)
            return
        }
        if (!tile.tilled) {
            tile.tilled = true
            addLog("Lahan dicangkul.")
            advance(5, 3)
            saveGame()
            return
        }
        val info = crops[selectedSeed]!!
        if (info.season != season) {
            addLog("Benih ${info.label} hanya untuk ${seasons[info.season]}.")
            return
        }
        if ((inventory[info.seed] ?: 0) <= 0) {
            addLog("Benih ${info.label} habis. Beli di toko.")
            return
        }
        inventory[info.seed] = inventory[info.seed]!! - 1
        tile.crop = Crop(selectedSeed, 0, weather == "Hujan")
        addLog("Menanam ${info.label}.")
        advance(10, 2)
        saveGame()
    }

    private fun openShop() {
        val items = arrayOf("Beli benih musim ini", "Jual semua hasil", "Beli ternak", "Beli pakan", "Keluar")
        AlertDialog.Builder(this)
            .setTitle("Toko Desa")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> buySeeds()
                    1 -> shipAll()
                    2 -> buyAnimal()
                    3 -> buyFeed()
                }
            }
            .show()
    }

    private fun buySeeds() {
        val info = crops[seedForSeason()]!!
        if (gold < 20) {
            toast("Uang tidak cukup.")
            return
        }
        gold -= 20
        inventory[info.seed] = (inventory[info.seed] ?: 0) + 3
        addLog("Beli 3 ${itemLabel(info.seed)}.")
        advance(10, 1)
        saveGame()
    }

    private fun buyFeed() {
        if (gold < 50) {
            toast("Uang tidak cukup.")
            return
        }
        gold -= 50
        inventory["feed"] = (inventory["feed"] ?: 0) + 5
        addLog("Beli 5 pakan.")
        advance(10, 1)
        saveGame()
    }

    private fun buyAnimal() {
        val items = arrayOf("Ayam - 300", "Sapi - 800", "Batal")
        AlertDialog.Builder(this)
            .setTitle("Beli Ternak")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> addAnimal("chicken", 300)
                    1 -> addAnimal("cow", 800)
                }
            }
            .show()
    }

    private fun addAnimal(kind: String, price: Int) {
        if (gold < price) {
            toast("Uang tidak cukup.")
            return
        }
        gold -= price
        val name = if (kind == "chicken") "Ayam ${animals.size + 1}" else "Sapi ${animals.size + 1}"
        animals.add(Animal(kind, name))
        addLog("$name bergabung dengan peternakan.")
        advance(20, 2)
        saveGame()
    }

    private fun shipAll() {
        var total = 0
        for (key in productPrices.keys + crops.values.map { it.produce }) {
            val qty = inventory[key] ?: 0
            if (qty == 0) continue
            val price = productPrices[key] ?: crops.values.first { it.produce == key }.price
            total += qty * price
            inventory[key] = 0
        }
        if (total == 0) {
            toast("Tidak ada hasil untuk dijual.")
            return
        }
        gold += total
        addLog("Hasil dikirim: +$total gold.")
        advance(10, 1)
        saveGame()
    }

    private fun openBarn() {
        val items = arrayOf("Beri pakan", "Kumpulkan hasil", "Belai ternak", "Beli ternak", "Keluar")
        AlertDialog.Builder(this)
            .setTitle("Kandang")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> feedAnimals()
                    1 -> collectProducts()
                    2 -> petAnimals()
                    3 -> buyAnimal()
                }
            }
            .show()
    }

    private fun feedAnimals() {
        if (animals.isEmpty()) {
            toast("Belum ada ternak.")
            return
        }
        if ((inventory["feed"] ?: 0) <= 0) {
            toast("Pakan habis. Beli di toko.")
            return
        }
        inventory["feed"] = inventory["feed"]!! - 1
        animals.forEach { it.fed = true }
        addLog("Semua ternak diberi pakan.")
        advance(10, 2)
        saveGame()
    }

    private fun collectProducts() {
        var found = 0
        for (animal in animals) {
            if (!animal.product) continue
            val item = if (animal.kind == "chicken") "egg" else "milk"
            inventory[item] = (inventory[item] ?: 0) + 1
            animal.product = false
            found++
        }
        if (found == 0) {
            toast("Belum ada hasil yang bisa diambil.")
            return
        }
        addLog("Mengambil $found hasil ternak.")
        advance(10, 2)
        saveGame()
    }

    private fun petAnimals() {
        if (animals.isEmpty()) {
            toast("Belum ada ternak.")
            return
        }
        animals.forEach { it.friendship = (it.friendship + 1).coerceAtMost(10) }
        addLog("Ternak jadi lebih sayang.")
        advance(10, 1)
        saveGame()
    }

    private fun talkLine(npc: Npc): String {
        val lines = when (npc.name) {
            "Aira" -> arrayOf(
                "Tanaman menyukai cuaca ${weather.lowercase()}!",
                "Aku suka lobak segar.",
                "Festival di hari ketujuh, jangan lupa!"
            )
            "Bima" -> arrayOf(
                "Peternakanmu makin ramai.",
                "Telur ayam hari ini masih hangat.",
                "Hati-hati kalau hujan, ladang bisa basah."
            )
            else -> arrayOf(
                "Labu musim gugur itu favoritku.",
                "Persahabatan butuh waktu, seperti tanaman.",
                "Kamu sudah punya ternak? Keren!"
            )
        }
        return lines[Random.nextInt(lines.size)]
    }

    private fun talkNpc(npc: Npc) {
        val status = if (npc.talkedToday) "Sudah ngobrol hari ini." else "Belum ngobrol hari ini."
        val builder = AlertDialog.Builder(this)
            .setTitle(npc.name + if (npc.spouse) " (pasangan)" else "")
            .setMessage("${talkLine(npc)}\n\nPersahabatan: ${npc.friendship}/10\n$status")
            .setPositiveButton("Beri hadiah") { _, _ -> giveGift(npc) }
            .setNegativeButton("Dekat", null)

        if (npc.friendship >= 8 && isFestival() && !npc.spouse && spouseName.isEmpty()) {
            builder.setNeutralButton("Ajak menikah") { _, _ ->
                npc.spouse = true
                spouseName = npc.name
                addLog("Kamu dan ${npc.name} menikah!")
                saveGame()
            }
        }
        if (!npc.talkedToday) {
            npc.talkedToday = true
            npc.friendship = (npc.friendship + 1).coerceAtMost(10)
            advance(10, 1)
            saveGame()
        }
        builder.show()
    }

    private fun giveGift(npc: Npc) {
        val keys = inventory.filter { it.value > 0 && it.key != "feed" }.keys.toList()
        if (keys.isEmpty()) {
            toast("Tidak ada barang untuk diberikan.")
            return
        }
        val labels = keys.map { itemLabel(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Beri hadiah ke ${npc.name}")
            .setItems(labels) { _, which ->
                val key = keys[which]
                inventory[key] = inventory[key]!! - 1
                val gain = if (key == npc.favorite) 2 else 1
                npc.friendship = (npc.friendship + gain).coerceAtMost(10)
                addLog("${npc.name} menerima ${itemLabel(key)}. (+$gain)")
                advance(10, 1)
                saveGame()
            }
            .show()
    }

    private fun openFestival() {
        val info = crops.values.firstOrNull { it.season == season && (inventory[it.produce] ?: 0) > 0 }
        if (info == null) {
            addLog("Festival: bawa hasil panen musim ${seasons[season]}!")
            return
        }
        inventory[info.produce] = inventory[info.produce]!! - 1
        val reward = info.price * 2
        gold += reward
        npcs.forEach { it.friendship = (it.friendship + 1).coerceAtMost(10) }
        addLog("Festival ${seasons[season]}: ${info.label} menang! +$reward")
        advance(30, 5)
        saveGame()
    }

    private fun openMenu() {
        val items = arrayOf("Lihat inventaris", "Pilih benih", "Simpan game", "Reset game", "Tutup")
        AlertDialog.Builder(this)
            .setTitle("Menu")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showInventory()
                    1 -> chooseSeed()
                    2 -> {
                        saveGame()
                        toast("Game tersimpan.")
                    }
                    3 -> confirmReset()
                }
            }
            .show()
    }

    private fun showInventory() {
        val text = inventory.filter { it.value > 0 }
            .entries.joinToString("\n") { "${itemLabel(it.key)}: ${it.value}" }
            .ifBlank { "Inventaris kosong." }
        AlertDialog.Builder(this)
            .setTitle("Inventaris")
            .setMessage(text + "\n\nGold: $gold\nStamina: $stamina")
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun chooseSeed() {
        val keys = crops.keys.toList()
        val labels = keys.map {
            val info = crops[it]!!
            "${itemLabel(info.seed)} (${inventory[info.seed] ?: 0}) - ${seasons[info.season]}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Pilih Benih")
            .setItems(labels) { _, which ->
                selectedSeed = keys[which]
                toast("Benih aktif: ${itemLabel(crops[selectedSeed]!!.seed)}")
            }
            .show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Reset game?")
            .setMessage("Semua progres akan hilang.")
            .setPositiveButton("Reset") { _, _ ->
                resetState()
                saveGame()
                redraw()
                toast("Game baru dimulai.")
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun saveGame() {
        val root = JSONObject()
        root.put("year", year)
        root.put("season", season)
        root.put("day", day)
        root.put("minutes", minutes)
        root.put("stamina", stamina)
        root.put("gold", gold)
        root.put("weather", weather)
        root.put("playerX", playerX)
        root.put("playerY", playerY)
        root.put("selectedSeed", selectedSeed)
        root.put("spouseName", spouseName)

        val tileRows = JSONArray()
        for (row in tiles) {
            val arr = JSONArray()
            for (tile in row) {
                val obj = JSONObject()
                obj.put("tilled", tile.tilled)
                val crop = tile.crop
                if (crop != null) {
                    val c = JSONObject()
                    c.put("kind", crop.kind)
                    c.put("growth", crop.growth)
                    c.put("watered", crop.watered)
                    obj.put("crop", c)
                }
                arr.put(obj)
            }
            tileRows.put(arr)
        }
        root.put("tiles", tileRows)

        val inv = JSONObject()
        for ((key, value) in inventory) inv.put(key, value)
        root.put("inventory", inv)

        val animalArr = JSONArray()
        for (animal in animals) {
            val obj = JSONObject()
            obj.put("kind", animal.kind)
            obj.put("name", animal.name)
            obj.put("friendship", animal.friendship)
            obj.put("fed", animal.fed)
            obj.put("product", animal.product)
            animalArr.put(obj)
        }
        root.put("animals", animalArr)

        val npcArr = JSONArray()
        for (npc in npcs) {
            val obj = JSONObject()
            obj.put("name", npc.name)
            obj.put("friendship", npc.friendship)
            obj.put("talkedToday", npc.talkedToday)
            obj.put("spouse", npc.spouse)
            npcArr.put(obj)
        }
        root.put("npcs", npcArr)

        val logArr = JSONArray()
        log.forEach { logArr.put(it) }
        root.put("log", logArr)

        prefs.edit().putString(SAVE_KEY, root.toString()).apply()
    }

    private fun loadGame() {
        resetState()
        val raw = prefs.getString(SAVE_KEY, null) ?: return
        try {
            val root = JSONObject(raw)
            year = root.optInt("year", year)
            season = root.optInt("season", season).coerceIn(0, 3)
            day = root.optInt("day", day).coerceIn(1, 7)
            minutes = root.optInt("minutes", minutes)
            stamina = root.optInt("stamina", stamina)
            gold = root.optInt("gold", gold)
            weather = root.optString("weather", weather)
            playerX = root.optInt("playerX", playerX)
            playerY = root.optInt("playerY", playerY)
            selectedSeed = root.optString("selectedSeed", selectedSeed)
            spouseName = root.optString("spouseName", "")

            val tileRows = root.optJSONArray("tiles")
            if (tileRows != null) {
                for (y in 0 until min(H, tileRows.length())) {
                    val arr = tileRows.getJSONArray(y)
                    for (x in 0 until min(W, arr.length())) {
                        val obj = arr.getJSONObject(x)
                        val tile = tiles[y][x]
                        tile.tilled = obj.optBoolean("tilled", false)
                        val c = obj.optJSONObject("crop")
                        if (c != null) {
                            tile.crop = Crop(
                                c.optString("kind", "turnip"),
                                c.optInt("growth", 0),
                                c.optBoolean("watered", false)
                            )
                        }
                    }
                }
            }

            val inv = root.optJSONObject("inventory")
            if (inv != null) {
                inventory.clear()
                val keys = inv.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    inventory[key] = inv.getInt(key)
                }
            }

            val animalArr = root.optJSONArray("animals")
            if (animalArr != null) {
                animals.clear()
                for (i in 0 until animalArr.length()) {
                    val obj = animalArr.getJSONObject(i)
                    animals.add(
                        Animal(
                            obj.optString("kind", "chicken"),
                            obj.optString("name", "Ternak"),
                            obj.optInt("friendship", 0),
                            obj.optBoolean("fed", false),
                            obj.optBoolean("product", false)
                        )
                    )
                }
            }

            val npcArr = root.optJSONArray("npcs")
            if (npcArr != null) {
                for (i in 0 until min(npcArr.length(), npcs.size)) {
                    val obj = npcArr.getJSONObject(i)
                    npcs[i].friendship = obj.optInt("friendship", 0)
                    npcs[i].talkedToday = obj.optBoolean("talkedToday", false)
                    npcs[i].spouse = obj.optBoolean("spouse", false)
                }
            }

            val logArr = root.optJSONArray("log")
            if (logArr != null && logArr.length() > 0) {
                log.clear()
                for (i in 0 until min(3, logArr.length())) log.add(logArr.getString(i))
            }
        } catch (e: Exception) {
            resetState()
        }
    }

    private inner class GameView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var worldOx = 0f
        private var worldOy = 0f
        private var worldTile = 1f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val controlH = dp(205).toFloat()
            val worldH = maxOf(dp(220).toFloat(), h - controlH)
            worldTile = min(w / W, worldH / H)
            worldOx = (w - worldTile * W) / 2f
            worldOy = dp(10).toFloat()

            canvas.drawColor(Color.parseColor("#8BC34A"))
            drawWorld(canvas, worldOx, worldOy, worldTile)
            drawHud(canvas, w)
            drawLog(canvas, w, worldOy + worldTile * H)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                val gx = ((event.x - worldOx) / worldTile).toInt()
                val gy = ((event.y - worldOy) / worldTile).toInt()
                if (gx in 0 until W && gy in 0 until H) startWalk(gx, gy)
            }
            return true
        }

        private fun drawWorld(canvas: Canvas, ox: Float, oy: Float, t: Float) {
            for (y in 0 until H) {
                for (x in 0 until W) {
                    paint.style = Paint.Style.FILL
                    paint.color = tileColor(x, y)
                    canvas.drawRect(ox + x * t, oy + y * t, ox + (x + 1) * t, oy + (y + 1) * t, paint)
                    paint.color = Color.parseColor("#1B5E2033")
                    canvas.drawRect(ox + x * t, oy + y * t, ox + (x + 1) * t, oy + y * t + 1, paint)
                }
            }

            drawBuilding(canvas, ox, oy, t, 0, 0, 2, 2, "#FFE0B2", "#D84315", "RUMAH", 1, 2)
            drawBuilding(canvas, ox, oy, t, 9, 0, 11, 2, "#B3E5FC", "#1565C0", "TOKO", 10, 2)
            drawBuilding(canvas, ox, oy, t, 0, 8, 2, 9, "#D7CCC8", "#5D4037", "KANDANG", 1, 8)
            drawShippingBin(canvas, ox, oy, t, 7, 3)
            if (isFestival()) drawFestival(canvas, ox, oy, t, 10, 5)

            for (y in FARM_Y1..FARM_Y2) {
                for (x in FARM_X1..FARM_X2) {
                    val crop = tiles[y][x].crop ?: continue
                    drawCrop(canvas, ox, oy, t, x, y, crop)
                }
            }

            for (animal in animals) {
                val index = animals.indexOf(animal)
                val ax = 1 + (index % 2)
                val ay = 8 + (index / 2)
                drawAnimal(canvas, ox, oy, t, ax, ay, animal)
            }

            for (npc in npcs) drawPerson(canvas, ox, oy, t, npc.x, npc.y, npc.color, npc.name)

            drawTrees(canvas, ox, oy, t)
            drawPerson(canvas, ox, oy, t, playerX, playerY, Color.parseColor("#FF7043"), "Kamu")
        }

        private fun tileColor(x: Int, y: Int): Int {
            return when {
                x in FARM_X1..FARM_X2 && y in FARM_Y1..FARM_Y2 -> {
                    val tile = tiles[y][x]
                    if (tile.tilled) {
                        if (tile.crop?.watered == true || weather == "Hujan") "#6D4C41" else "#8D6E63"
                    } else "#7CB342"
                }
                x in 8..11 && y in 5..8 -> "#CFCABB"
                x in 0..2 && y in 0..2 -> "#A1887F"
                x in 9..11 && y in 0..2 -> "#90A4AE"
                x in 0..2 && y in 8..9 -> "#A1887F"
                else -> "#8BC34A"
            }.let { Color.parseColor(it) }
        }

        private fun drawBuilding(
            canvas: Canvas,
            ox: Float,
            oy: Float,
            t: Float,
            x1: Int,
            y1: Int,
            x2: Int,
            y2: Int,
            wall: String,
            roof: String,
            label: String,
            doorX: Int,
            doorY: Int
        ) {
            val left = ox + x1 * t
            val top = oy + y1 * t
            val right = ox + (x2 + 1) * t
            val bottom = oy + (y2 + 1) * t
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor(wall)
            canvas.drawRect(left + 2, top + t * 0.35f, right - 2, bottom - 2, paint)

            val path = Path()
            path.moveTo(left, top + t * 0.4f)
            path.lineTo((left + right) / 2f, top - t * 0.15f)
            path.lineTo(right, top + t * 0.4f)
            path.close()
            paint.color = Color.parseColor(roof)
            canvas.drawPath(path, paint)

            paint.color = Color.parseColor("#5D4037")
            canvas.drawRect(
                ox + doorX * t + t * 0.3f,
                oy + doorY * t + t * 0.15f,
                ox + doorX * t + t * 0.7f,
                oy + doorY * t + t * 0.95f,
                paint
            )

            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = t * 0.22f
            paint.isFakeBoldText = true
            canvas.drawText(label, (left + right) / 2f, top + t * 0.78f, paint)
            paint.isFakeBoldText = false
        }

        private fun drawShippingBin(canvas: Canvas, ox: Float, oy: Float, t: Float, x: Int, y: Int) {
            val cx = ox + x * t + t / 2f
            val cy = oy + y * t + t / 2f
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#8D6E63")
            canvas.drawRect(cx - t * 0.35f, cy - t * 0.2f, cx + t * 0.35f, cy + t * 0.3f, paint)
            paint.color = Color.parseColor("#5D4037")
            canvas.drawRect(cx - t * 0.38f, cy - t * 0.3f, cx + t * 0.38f, cy - t * 0.15f, paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = t * 0.18f
            canvas.drawText("JUAL", cx, cy + t * 0.62f, paint)
        }

        private fun drawFestival(canvas: Canvas, ox: Float, oy: Float, t: Float, x: Int, y: Int) {
            val cx = ox + x * t + t / 2f
            val cy = oy + y * t + t / 2f
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#FFCA28")
            canvas.drawCircle(cx, cy, t * 0.28f, paint)
            paint.color = Color.parseColor("#E91E63")
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = t * 0.18f
            canvas.drawText("FEST", cx, cy + t * 0.06f, paint)
        }

        private fun drawCrop(canvas: Canvas, ox: Float, oy: Float, t: Float, x: Int, y: Int, crop: Crop) {
            val info = crops[crop.kind] ?: return
            val ratio = (crop.growth.toFloat() / info.days).coerceIn(0f, 1f)
            val cx = ox + x * t + t / 2f
            val cy = oy + y * t + t / 2f
            paint.style = Paint.Style.FILL
            paint.color = if (ratio >= 1f) Color.parseColor("#FDD835") else Color.parseColor("#43A047")
            canvas.drawCircle(cx, cy, t * (0.12f + ratio * 0.16f), paint)
            paint.color = Color.parseColor("#2E7D32")
            canvas.drawCircle(cx - t * 0.12f, cy + t * 0.12f, t * 0.08f, paint)
            if (crop.watered) {
                paint.color = Color.parseColor("#4FC3F7")
                canvas.drawCircle(cx + t * 0.22f, cy - t * 0.18f, t * 0.05f, paint)
            }
        }

        private fun drawAnimal(canvas: Canvas, ox: Float, oy: Float, t: Float, x: Int, y: Int, animal: Animal) {
            val cx = ox + x * t + t / 2f
            val cy = oy + y * t + t / 2f
            paint.style = Paint.Style.FILL
            if (animal.kind == "chicken") {
                paint.color = Color.WHITE
                canvas.drawOval(cx - t * 0.22f, cy - t * 0.16f, cx + t * 0.22f, cy + t * 0.2f, paint)
                paint.color = Color.parseColor("#F57F17")
                canvas.drawCircle(cx + t * 0.14f, cy - t * 0.18f, t * 0.05f, paint)
            } else {
                paint.color = Color.WHITE
                canvas.drawRect(cx - t * 0.26f, cy - t * 0.18f, cx + t * 0.26f, cy + t * 0.2f, paint)
                paint.color = Color.parseColor("#424242")
                canvas.drawCircle(cx - t * 0.12f, cy, t * 0.07f, paint)
                canvas.drawCircle(cx + t * 0.1f, cy + t * 0.08f, t * 0.06f, paint)
            }
            if (animal.product) {
                paint.color = Color.parseColor("#FFD54F")
                canvas.drawCircle(cx + t * 0.22f, cy - t * 0.24f, t * 0.06f, paint)
            }
        }

        private fun drawTrees(canvas: Canvas, ox: Float, oy: Float, t: Float) {
            val spots = arrayOf(
                3 to 1, 6 to 1, 7 to 6, 4 to 9, 7 to 9, 11 to 9
            )
            for ((x, y) in spots) {
                val cx = ox + x * t + t / 2f
                val cy = oy + y * t + t / 2f
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#5D4037")
                canvas.drawRect(cx - t * 0.05f, cy, cx + t * 0.05f, cy + t * 0.3f, paint)
                paint.color = Color.parseColor("#2E7D32")
                canvas.drawCircle(cx, cy - t * 0.08f, t * 0.26f, paint)
                paint.color = Color.parseColor("#43A047")
                canvas.drawCircle(cx - t * 0.12f, cy - t * 0.18f, t * 0.16f, paint)
            }
        }

        private fun drawPerson(
            canvas: Canvas,
            ox: Float,
            oy: Float,
            t: Float,
            gx: Int,
            gy: Int,
            color: Int,
            name: String
        ) {
            val cx = ox + gx * t + t / 2f
            val cy = oy + gy * t + t / 2f
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#00000033")
            canvas.drawOval(cx - t * 0.22f, cy + t * 0.24f, cx + t * 0.22f, cy + t * 0.34f, paint)
            paint.color = color
            canvas.drawCircle(cx, cy, t * 0.24f, paint)
            paint.color = Color.parseColor("#FFE0B2")
            canvas.drawCircle(cx, cy - t * 0.06f, t * 0.16f, paint)
            paint.color = Color.parseColor("#3E2723")
            canvas.drawCircle(cx - t * 0.06f, cy - t * 0.08f, t * 0.025f, paint)
            canvas.drawCircle(cx + t * 0.06f, cy - t * 0.08f, t * 0.025f, paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = t * 0.18f
            canvas.drawText(name, cx, cy - t * 0.32f, paint)
        }

        private fun drawHud(canvas: Canvas, w: Float) {
            val pad = dp(8).toFloat()
            val height = dp(58).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#CC1B5E20")
            canvas.drawRoundRect(RectF(pad, pad, w - pad, pad + height), dp(12).toFloat(), dp(12).toFloat(), paint)

            val hour = minutes / 60
            val min = minutes % 60
            val time = String.format("%02d:%02d", hour, min)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = dp(13).toFloat()
            paint.isFakeBoldText = true
            canvas.drawText(
                "Tahun $year • ${seasons[season]} Hari $day • $weather • $time",
                pad + dp(10),
                pad + dp(20),
                paint
            )
            canvas.drawText("Gold: $gold", pad + dp(10), pad + dp(42), paint)
            paint.isFakeBoldText = false

            val barX = w - dp(120)
            val barY = pad + dp(30)
            paint.color = Color.parseColor("#33FFFFFF")
            canvas.drawRoundRect(
                RectF(barX, barY, barX + dp(100), barY + dp(12)),
                dp(6).toFloat(), dp(6).toFloat(), paint
            )
            paint.color = if (stamina > 30) Color.parseColor("#FFEB3B") else Color.parseColor("#FF5252")
            val ratio = (stamina.coerceIn(0, 100) / 100f)
            canvas.drawRoundRect(
                RectF(barX, barY, barX + dp(100) * ratio, barY + dp(12)),
                dp(6).toFloat(), dp(6).toFloat(), paint
            )
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = dp(11).toFloat()
            canvas.drawText("Stamina", barX, barY - dp(4), paint)
        }

        private fun drawLog(canvas: Canvas, w: Float, worldBottom: Float) {
            val pad = dp(8).toFloat()
            val top = worldBottom + dp(6)
            val height = dp(62).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#AA000000")
            canvas.drawRoundRect(RectF(pad, top, w - pad, top + height), dp(10).toFloat(), dp(10).toFloat(), paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = dp(12).toFloat()
            val lines = log.take(2)
            for (i in lines.indices) {
                canvas.drawText(lines[i], pad + dp(10), top + dp(22) + i * dp(20), paint)
            }
            paint.textAlign = Paint.Align.CENTER
        }
    }
}
