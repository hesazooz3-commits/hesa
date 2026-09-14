package com.a7a.cards

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.UUID
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {

    data class Card(val id: String, val network: String, val number: String, var used: Boolean)

    private val prefs by lazy { getSharedPreferences("cards", MODE_PRIVATE) }
    private val cards = mutableListOf<Card>()
    private lateinit var listContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var countText: TextView
    private lateinit var unusedText: TextView
    private lateinit var usedText: TextView
    private lateinit var autoButton: Button
    private lateinit var stopButton: Button
    private lateinit var filterAll: Button
    private lateinit var filterUnused: Button
    private lateinit var filterUsed: Button

    private var filterMode = 0 // 0 all, 1 unused, 2 used
    private val handler = Handler(Looper.getMainLooper())
    private var autoChargeRunning = false
    private var autoChargeIndex = 0
    private var autoChargeCards = emptyList<Card>()
    private var pendingAutoCharge = false
    private var pendingCode: String? = null

    private val networks = listOf("Orange", "Vodafone", "WE", "e& Egypt")
    private val prefixes = mapOf(
        "Orange" to "#102*%s#",
        "Vodafone" to "*858*%s#",
        "WE" to "*555*%s#",
        "e& Egypt" to "*556*%s#"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadCards()
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.rgb(246, 247, 251))
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(4), dp(5), dp(4), dp(10))
        }
        val brand = TextView(this).apply {
            text = "A7A Cards"
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(27, 31, 45))
            gravity = Gravity.CENTER_VERTICAL
        }
        val tagline = TextView(this).apply {
            text = "إدارة كروت الشحن"
            textSize = 12f
            setTextColor(Color.rgb(105, 110, 125))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        header.addView(brand, LinearLayout.LayoutParams(0, dp(46), 1f))
        header.addView(tagline, lp(-2, dp(46)))
        root.addView(header, lp(-1, dp(56)))

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        countText = statCard(stats, "كل الكروت", "0")
        unusedText = statCard(stats, "جاهز للشحن", "0")
        usedText = statCard(stats, "مستخدم", "0")
        root.addView(stats, lp(-1, dp(82)))

        val mainActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, dp(9), 0, dp(4))
        }
        mainActions.addView(actionButton("＋  إضافة كارت", true) { showAddDialog() }, weightLp())
        mainActions.addView(actionButton("▣  من الرسائل", false) { showExtractDialog() }, weightLp())
        root.addView(mainActions, lp(-1, dp(56)))

        val chargeActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, dp(4), 0, dp(4))
        }
        autoButton = actionButton("⚡  شحن كل الجاهز", true) { confirmAutoCharge() }
        stopButton = actionButton("■  إيقاف الشحن", false) { stopAutoCharge() }
        stopButton.visibility = View.GONE
        chargeActions.addView(autoButton, weightLp())
        chargeActions.addView(stopButton, weightLp())
        root.addView(chargeActions, lp(-1, dp(56)))

        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = rounded(Color.WHITE, 18, Color.rgb(224, 226, 234))
            setPadding(dp(10), 0, dp(10), 0)
        }
        val icon = TextView(this).apply {
            text = "⌕"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(90, 95, 110))
        }
        searchInput = EditText(this).apply {
            hint = "ابحث برقم الكارت أو الشبكة"
            textSize = 14f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            background = null
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(7), 0, dp(7), 0)
        }
        searchBox.addView(icon, lp(dp(32), -1))
        searchBox.addView(searchInput, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(searchBox, lp(-1, dp(50)).apply { setMargins(0, dp(8), 0, dp(6)) })
        searchInput.addTextChangedListener(SimpleTextWatcher { renderCards() })

        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        filterAll = filterButton("الكل") { filterMode = 0; updateFilterButtons(); renderCards() }
        filterUnused = filterButton("غير مستخدم") { filterMode = 1; updateFilterButtons(); renderCards() }
        filterUsed = filterButton("مستخدم") { filterMode = 2; updateFilterButtons(); renderCards() }
        filterRow.addView(filterAll, weightLp())
        filterRow.addView(filterUnused, weightLp())
        filterRow.addView(filterUsed, weightLp())
        root.addView(filterRow, lp(-1, dp(46)))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(LinearLayout(this@MainActivity).also { listContainer = it }.apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(0, dp(5), 0, dp(12))
            }, lp(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, dp(5), 0, 0)
        }
        val clearUsed = Button(this).apply {
            text = "🗑  مسح الكروت المستخدمة"
            textSize = 13f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(175, 52, 52))
            background = rounded(Color.rgb(255, 244, 244), 16, Color.rgb(244, 210, 210))
            stateListAnimator = null
            setOnClickListener { confirmClearUsed() }
        }
        val dial = Button(this).apply {
            text = "⌨  لوحة الاتصال"
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.rgb(55, 59, 72))
            background = rounded(Color.WHITE, 16, Color.rgb(220, 222, 230))
            stateListAnimator = null
            setOnClickListener { showDialPad() }
        }
        bottomRow.addView(clearUsed, weightLp())
        bottomRow.addView(dial, weightLp())
        root.addView(bottomRow, lp(-1, dp(52)))

        setContentView(root)
        updateFilterButtons()
        renderCards()
    }

    private fun statCard(parent: LinearLayout, label: String, value: String): TextView {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, 17, Color.TRANSPARENT)
        }
        val valueView = TextView(this).apply {
            text = value
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(35, 39, 52))
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(115, 119, 132))
        }
        box.addView(valueView, lp(-1, dp(34)))
        box.addView(labelView, lp(-1, dp(24)))
        parent.addView(box, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        return valueView
    }

    private fun actionButton(textValue: String, primary: Boolean, click: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 13f
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        setOnClickListener { click() }
        setTextColor(if (primary) Color.WHITE else Color.rgb(50, 54, 66))
        background = if (primary) rounded(Color.rgb(66, 80, 205), 16, Color.TRANSPARENT)
        else rounded(Color.WHITE, 16, Color.rgb(220, 222, 230))
        stateListAnimator = null
    }

    private fun filterButton(textValue: String, click: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 12f
        isAllCaps = false
        setOnClickListener { click() }
        stateListAnimator = null
    }

    private fun updateFilterButtons() {
        if (!::filterAll.isInitialized) return
        styleFilter(filterAll, filterMode == 0)
        styleFilter(filterUnused, filterMode == 1)
        styleFilter(filterUsed, filterMode == 2)
    }

    private fun styleFilter(button: Button, selected: Boolean) {
        button.setTextColor(if (selected) Color.WHITE else Color.rgb(65, 69, 82))
        button.background = if (selected) rounded(Color.rgb(66, 80, 205), 14, Color.TRANSPARENT)
        else rounded(Color.WHITE, 14, Color.rgb(224, 226, 234))
    }

    private fun renderCards() {
        if (!::listContainer.isInitialized) return
        listContainer.removeAllViews()
        val total = cards.size
        val unused = cards.count { !it.used }
        countText.text = total.toString()
        unusedText.text = unused.toString()
        usedText.text = (total - unused).toString()

        val q = searchInput.text.toString().trim()
        val visible = cards.filter {
            val matchesSearch = q.isBlank() || it.number.contains(q) || it.network.contains(q, true)
            val matchesFilter = when (filterMode) {
                1 -> !it.used
                2 -> it.used
                else -> true
            }
            matchesSearch && matchesFilter
        }.sortedBy { it.used }

        if (visible.isEmpty()) {
            val empty = TextView(this).apply {
                text = when {
                    cards.isEmpty() -> "مفيش كروت لسه\n\nاضغط «إضافة كارت» أو «من الرسائل» للبدء"
                    filterMode == 1 -> "مفيش كروت غير مستخدمة"
                    filterMode == 2 -> "مفيش كروت مستخدمة"
                    else -> "مش لاقي كروت مطابقة للبحث"
                }
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(105, 109, 122))
                setPadding(dp(20), dp(55), dp(20), dp(55))
            }
            listContainer.addView(empty, lp(-1, -2))
            return
        }
        visible.forEach { addCardView(it) }
    }

    private fun addCardView(card: Card) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(15), dp(13), dp(15), dp(13))
            background = rounded(Color.WHITE, 20, Color.TRANSPARENT)
            elevation = dp(2).toFloat()
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val network = TextView(this).apply {
            text = card.network
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(35, 39, 52))
        }
        val status = TextView(this).apply {
            text = if (card.used) "مستخدم" else "جاهز"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (card.used) Color.rgb(100, 104, 115) else Color.rgb(32, 128, 82))
            background = rounded(if (card.used) Color.rgb(239, 240, 244) else Color.rgb(231, 248, 239), 20, Color.TRANSPARENT)
            setPadding(dp(11), dp(4), dp(11), dp(4))
        }
        top.addView(network, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(status, lp(-2, dp(30)))
        box.addView(top)

        val number = TextView(this).apply {
            text = formatNumber(card.number)
            textSize = 20f
            typeface = Typeface.create("monospace", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(45, 49, 62))
            background = rounded(Color.rgb(247, 248, 251), 14, Color.TRANSPARENT)
            setPadding(dp(8), dp(7), dp(8), dp(7))
        }
        box.addView(number, lp(-1, dp(49)).apply { setMargins(0, dp(9), 0, dp(9)) })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val charge = smallButton("شحن", Color.rgb(66, 80, 205), Color.WHITE) { chargeCard(card) }
        val copy = smallButton("نسخ", Color.rgb(244, 245, 248), Color.rgb(55, 59, 70)) {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("card", card.number))
            Toast.makeText(this, "تم نسخ رقم الكارت", Toast.LENGTH_SHORT).show()
        }
        val toggle = smallButton(if (card.used) "إرجاع" else "تم استخدامه", Color.rgb(244, 245, 248), Color.rgb(55, 59, 70)) {
            card.used = !card.used
            saveCards(); renderCards()
        }
        val delete = smallButton("حذف", Color.rgb(255, 242, 242), Color.rgb(190, 55, 55)) { confirmDelete(card) }
        row.addView(charge, weightLp())
        row.addView(copy, weightLp())
        row.addView(toggle, weightLp())
        row.addView(delete, weightLp())
        box.addView(row, lp(-1, dp(46)))
        listContainer.addView(box, lp(-1, -2).apply { setMargins(0, 0, 0, dp(9)) })
    }

    private fun smallButton(textValue: String, bg: Int, fg: Int, click: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 11.5f
        isAllCaps = false
        setTextColor(fg)
        background = rounded(bg, 13, Color.TRANSPARENT)
        setOnClickListener { click() }
        stateListAnimator = null
    }

    private fun formatNumber(number: String): String = number.chunked(4).joinToString("  ")

    private fun confirmDelete(card: Card) {
        AlertDialogBuilder(this)
            .setTitle("حذف الكارت؟")
            .setMessage("هل تريد حذف كارت ${card.number}؟")
            .setPositiveButton("حذف") { _, _ -> cards.remove(card); saveCards(); renderCards() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmClearUsed() {
        val usedCount = cards.count { it.used }
        if (usedCount == 0) {
            Toast.makeText(this, "مفيش كروت مستخدمة للمسح", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialogBuilder(this)
            .setTitle("مسح الكروت المستخدمة")
            .setMessage("هيتم حذف $usedCount كرت مستخدم نهائيًا من التطبيق. الكروت الجاهزة للشحن مش هتتأثر.")
            .setPositiveButton("مسح الكل") { _, _ ->
                cards.removeAll { it.used }
                saveCards()
                renderCards()
                Toast.makeText(this, "تم مسح $usedCount كرت مستخدم", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun confirmAutoCharge() {
        if (autoChargeRunning) {
            Toast.makeText(this, "الشحن التلقائي يعمل بالفعل", Toast.LENGTH_SHORT).show()
            return
        }
        val unused = cards.filter { !it.used }
        if (unused.isEmpty()) {
            Toast.makeText(this, "لا توجد كروت غير مستخدمة", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialogBuilder(this)
            .setTitle("⚡ شحن كل الكروت الجاهزة")
            .setMessage("هيتم شحن ${unused.size} كرت بالتتابع، بفاصل 10 ثوانٍ بين كل كرت.\n\nالكروت المستخدمة هتتخطى تلقائيًا.")
            .setPositiveButton("بدء الشحن") { _, _ -> startAutoCharge() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun startAutoCharge() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingAutoCharge = true
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), REQUEST_AUTO_CALL)
            return
        }
        autoChargeCards = cards.filter { !it.used }
        autoChargeIndex = 0
        if (autoChargeCards.isEmpty()) return
        pendingAutoCharge = false
        autoChargeRunning = true
        autoButton.visibility = View.GONE
        stopButton.visibility = View.VISIBLE
        chargeNextAutomatically()
    }

    private fun chargeNextAutomatically() {
        if (!autoChargeRunning) return
        if (autoChargeIndex >= autoChargeCards.size) {
            finishAutoCharge()
            return
        }
        val card = autoChargeCards[autoChargeIndex]
        val code = String.format(prefixes.getValue(card.network), card.number)
        val started = dialUssd(code)
        if (!started) {
            stopAutoCharge()
            Toast.makeText(this, "تعذر بدء شحن الكارت الحالي", Toast.LENGTH_LONG).show()
            return
        }
        card.used = true
        saveCards()
        renderCards()
        autoChargeIndex++
        if (autoChargeIndex < autoChargeCards.size) {
            handler.postDelayed({ chargeNextAutomatically() }, AUTO_CHARGE_DELAY_MS)
        } else {
            finishAutoCharge()
        }
    }

    private fun finishAutoCharge() {
        autoChargeRunning = false
        handler.removeCallbacksAndMessages(null)
        autoChargeCards = emptyList()
        autoChargeIndex = 0
        autoButton.visibility = View.VISIBLE
        stopButton.visibility = View.GONE
        Toast.makeText(this, "تم الانتهاء من الشحن التلقائي", Toast.LENGTH_LONG).show()
    }

    private fun stopAutoCharge() {
        val wasRunning = autoChargeRunning
        autoChargeRunning = false
        pendingAutoCharge = false
        handler.removeCallbacksAndMessages(null)
        autoChargeCards = emptyList()
        autoChargeIndex = 0
        if (::autoButton.isInitialized) autoButton.visibility = View.VISIBLE
        if (::stopButton.isInitialized) stopButton.visibility = View.GONE
        if (wasRunning) Toast.makeText(this, "تم إيقاف الشحن التلقائي", Toast.LENGTH_SHORT).show()
    }

    private fun showAddDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(4), dp(22), dp(4)) }
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, networks)
        val number = EditText(this).apply {
            hint = "رقم الكارت"
            inputType = InputType.TYPE_CLASS_PHONE
            isSingleLine = true
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        layout.addView(spinner, lp(-1, dp(52)))
        layout.addView(number, lp(-1, dp(58)).apply { setMargins(0, dp(8), 0, 0) })
        AlertDialogBuilder(this).setTitle("إضافة كارت جديد").setView(layout)
            .setPositiveButton("حفظ") { _, _ ->
                val clean = normalizeDigits(number.text.toString()).filter(Char::isDigit)
                if (clean.isNotBlank()) addCard(spinner.selectedItem.toString(), clean)
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun showExtractDialog() {
        val input = EditText(this).apply {
            hint = "الصق رسالة واحدة أو عدة رسائل هنا…"
            minLines = 7
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        AlertDialogBuilder(this)
            .setTitle("إضافة الكروت من الرسائل")
            .setMessage("الصق رسالة أو مجموعة رسائل. التطبيق هيستخرج كل أرقام الكروت الموجودة، مش أول رقم بس.")
            .setView(input)
            .setPositiveButton("استخراج الكل") { _, _ ->
                val numbers = extractCardNumbers(input.text.toString())
                if (numbers.isEmpty()) {
                    Toast.makeText(this, "لم يتم العثور على أرقام كروت مناسبة", Toast.LENGTH_LONG).show()
                } else {
                    showAddManyWithNetwork(numbers)
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showAddManyWithNetwork(numbers: List<String>) {
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, networks)
        val info = TextView(this).apply {
            text = "تم العثور على ${numbers.size} كرت\n\n${numbers.joinToString("\n") { "• " + formatNumber(it) }}"
            textSize = 14f
            setTextColor(Color.rgb(55, 59, 72))
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(info, lp(-1, -2))
            addView(spinner, lp(-1, dp(52)))
        }
        AlertDialogBuilder(this)
            .setTitle("إضافة ${numbers.size} كرت")
            .setView(layout)
            .setPositiveButton("إضافة الكل") { _, _ -> addCards(spinner.selectedItem.toString(), numbers) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun normalizeDigits(text: String): String = text.map { ch ->
        when (ch) {
            in '\u0660'..'\u0669' -> ('0'.code + ch.code - '\u0660'.code).toChar()
            in '\u06F0'..'\u06F9' -> ('0'.code + ch.code - '\u06F0'.code).toChar()
            else -> ch
        }
    }.joinToString("")

    private fun extractCardNumbers(text: String): List<String> {
        val result = LinkedHashSet<String>()
        val normalized = normalizeDigits(text)
        val matcher = Pattern.compile("(?<!\\d)(?:\\d[ \\u200E\\u200F-]?){12,20}(?![0-9])").matcher(normalized)
        while (matcher.find()) {
            val clean = matcher.group().filter(Char::isDigit)
            if (clean.length in 12..20) result.add(clean)
        }
        val fallback = Pattern.compile("(?<!\\d)\\d{12,20}(?!\\d)").matcher(normalized)
        while (fallback.find()) result.add(fallback.group())
        return result.toList()
    }

    private fun addCards(network: String, numbers: List<String>) {
        var added = 0
        var duplicate = 0
        numbers.forEach { number ->
            if (number.length !in 12..20) return@forEach
            if (cards.any { it.number == number }) duplicate++
            else {
                cards.add(Card(UUID.randomUUID().toString(), network, number, false))
                added++
            }
        }
        saveCards()
        renderCards()
        Toast.makeText(this, "تمت إضافة $added كرت${if (duplicate > 0) " • $duplicate موجود بالفعل" else ""}", Toast.LENGTH_LONG).show()
    }

    private fun addCard(network: String, number: String) {
        if (number.length !in 12..20) {
            Toast.makeText(this, "رقم الكارت يجب أن يكون من 12 إلى 20 رقم", Toast.LENGTH_SHORT).show()
            return
        }
        if (cards.any { it.number == number }) {
            Toast.makeText(this, "الكارت موجود بالفعل", Toast.LENGTH_SHORT).show()
            return
        }
        cards.add(Card(UUID.randomUUID().toString(), network, number, false))
        saveCards(); renderCards()
        Toast.makeText(this, "تمت إضافة الكارت", Toast.LENGTH_SHORT).show()
    }

    private fun chargeCard(card: Card) {
        val code = String.format(prefixes.getValue(card.network), card.number)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pendingCode = code
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL)
            return
        }
        if (dialUssd(code)) {
            card.used = true
            saveCards(); renderCards()
        }
    }

    private fun dialUssd(code: String): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(code)}")))
            true
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر فتح الاتصال", Toast.LENGTH_LONG).show()
            false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        val granted = results.firstOrNull() == PackageManager.PERMISSION_GRANTED
        if (requestCode == REQUEST_CALL && granted) {
            pendingCode?.let { dialUssd(it) }
            pendingCode = null
        } else if (requestCode == REQUEST_AUTO_CALL && granted && pendingAutoCharge) {
            pendingAutoCharge = false
            startAutoCharge()
        } else if ((requestCode == REQUEST_CALL || requestCode == REQUEST_AUTO_CALL) && !granted) {
            pendingCode = null
            pendingAutoCharge = false
            Toast.makeText(this, "لا يمكن إجراء الاتصال بدون إذن المكالمات", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDialPad() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8)) }
        val display = EditText(this).apply { inputType = InputType.TYPE_CLASS_PHONE; textSize = 22f; gravity = Gravity.CENTER; isSingleLine = true }
        layout.addView(display, lp(-1, dp(58)))
        val keys = arrayOf(arrayOf("1", "2", "3"), arrayOf("4", "5", "6"), arrayOf("7", "8", "9"), arrayOf("*", "0", "#"))
        keys.forEach { rowKeys ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowKeys.forEach { key ->
                row.addView(Button(this).apply {
                    text = key; textSize = 20f; isAllCaps = false
                    background = rounded(Color.rgb(246, 247, 250), 14, Color.TRANSPARENT)
                    stateListAnimator = null
                    setOnClickListener { display.append(key) }
                }, weightLp())
            }
            layout.addView(row, lp(-1, dp(58)))
        }
        layout.addView(Button(this).apply {
            text = "اتصال"; textSize = 16f; isAllCaps = false; setTextColor(Color.WHITE)
            background = rounded(Color.rgb(66, 80, 205), 16, Color.TRANSPARENT)
            stateListAnimator = null
            setOnClickListener {
                val code = display.text.toString()
                if (code.isBlank()) return@setOnClickListener
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    pendingCode = code
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL)
                } else dialUssd(code)
            }
        }, lp(-1, dp(56)).apply { setMargins(0, dp(8), 0, 0) })
        AlertDialogBuilder(this).setTitle("لوحة الاتصال").setView(layout).setNegativeButton("إغلاق", null).show()
    }

    private fun saveCards() {
        val data = cards.joinToString("\n") { listOf(it.id, it.network, it.number, it.used).joinToString("|") }
        prefs.edit().putString("data", data).apply()
    }

    private fun loadCards() {
        cards.clear()
        prefs.getString("data", "")?.lines()?.filter { it.isNotBlank() }?.forEach { line ->
            val p = line.split("|")
            if (p.size == 4) cards.add(Card(p[0], p[1], p[2], p[3].toBoolean()))
        }
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != Color.TRANSPARENT) setStroke(dp(1), strokeColor)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private fun weightLp() = LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        autoChargeRunning = false
        super.onDestroy()
    }

    companion object {
        const val REQUEST_CALL = 501
        const val REQUEST_AUTO_CALL = 502
        const val AUTO_CHARGE_DELAY_MS = 10_000L
    }
}

private class SimpleTextWatcher(private val action: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { action() }
    override fun afterTextChanged(s: android.text.Editable?) {}
}

private fun AlertDialogBuilder(context: Context): androidx.appcompat.app.AlertDialog.Builder =
    androidx.appcompat.app.AlertDialog.Builder(context)
