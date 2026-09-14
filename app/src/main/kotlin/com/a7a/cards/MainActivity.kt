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
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(14))
        }
        val title = TextView(this).apply {
            text = "A7A Cards"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(25, 28, 38))
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "إدارة كروت الشحن بسهولة وأمان"
            textSize = 14f
            setTextColor(Color.rgb(100, 106, 120))
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, 0)
        }
        header.addView(title, lp(-1, -2))
        header.addView(subtitle, lp(-1, -2))
        root.addView(header, lp(-1, -2))

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        countText = statCard(stats, "إجمالي", "0")
        unusedText = statCard(stats, "متبقي", "0")
        usedText = statCard(stats, "مستخدم", "0")
        root.addView(stats, lp(-1, dp(86)))

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, dp(10), 0, dp(6))
        }
        val add = actionButton("＋  إضافة كارت", true) { showAddDialog() }
        val extract = actionButton("▣  من رسالة", false) { showExtractDialog() }
        actionRow.addView(add, weightLp())
        actionRow.addView(extract, weightLp())
        root.addView(actionRow, lp(-1, dp(58)))

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        autoButton = actionButton("⚡  شحن كل الكروت", true) { confirmAutoCharge() }
        stopButton = actionButton("■  إيقاف", false) { stopAutoCharge() }
        stopButton.visibility = View.GONE
        tools.addView(autoButton, weightLp())
        tools.addView(stopButton, weightLp())
        root.addView(tools, lp(-1, dp(58)))

        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = rounded(Color.WHITE, 18f, Color.rgb(225, 227, 234))
            setPadding(dp(12), 0, dp(12), 0)
        }
        val icon = TextView(this).apply {
            text = "⌕"
            textSize = 25f
            setTextColor(Color.rgb(95, 99, 112))
            gravity = Gravity.CENTER
        }
        searchInput = EditText(this).apply {
            hint = "ابحث برقم الكارت أو الشبكة"
            textSize = 15f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            background = null
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(8), 0, dp(8), 0)
        }
        searchBox.addView(icon, lp(dp(34), -1))
        searchBox.addView(searchInput, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(searchBox, lp(-1, dp(52)).apply { setMargins(0, dp(10), 0, dp(8)) })
        searchInput.addTextChangedListener(SimpleTextWatcher { renderCards() })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(LinearLayout(this@MainActivity).also { listContainer = it }.apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(0, 0, 0, dp(16))
            }, lp(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val dial = Button(this).apply {
            text = "⌨  لوحة الاتصال"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.rgb(55, 59, 72))
            background = rounded(Color.WHITE, 18f, Color.rgb(220, 222, 230))
            setOnClickListener { showDialPad() }
        }
        root.addView(dial, lp(-1, dp(54)).apply { setMargins(0, dp(6), 0, 0) })

        setContentView(root)
        renderCards()
    }

    private fun statCard(parent: LinearLayout, label: String, value: String): TextView {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, 18f, Color.TRANSPARENT)
        }
        val valueView = TextView(this).apply {
            text = value
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(35, 39, 52))
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(115, 119, 132))
        }
        box.addView(valueView, lp(-1, dp(34)))
        box.addView(labelView, lp(-1, dp(24)))
        parent.addView(box, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        return valueView
    }

    private fun actionButton(textValue: String, primary: Boolean, click: () -> Unit): Button = Button(this).apply {
        text = textValue
        textSize = 14f
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        setOnClickListener { click() }
        setTextColor(if (primary) Color.WHITE else Color.rgb(50, 54, 66))
        background = if (primary) rounded(Color.rgb(68, 82, 210), 17f, Color.TRANSPARENT)
        else rounded(Color.WHITE, 17f, Color.rgb(220, 222, 230))
        stateListAnimator = null
    }

    private fun renderCards() {
        if (!::listContainer.isInitialized) return
        listContainer.removeAllViews()
        val total = cards.size
        val unused = cards.count { !it.used }
        val used = total - unused
        countText.text = total.toString()
        unusedText.text = unused.toString()
        usedText.text = used.toString()

        val q = searchInput.text.toString().trim()
        val visible = cards.filter { q.isBlank() || it.number.contains(q) || it.network.contains(q, true) }
        if (visible.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (cards.isEmpty()) "مفيش كروت لسه\nاضغط «إضافة كارت» للبدء" else "مش لاقي كروت مطابقة للبحث"
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(105, 109, 122))
                setPadding(dp(20), dp(60), dp(20), dp(60))
            }
            listContainer.addView(empty, lp(-1, -2))
            return
        }

        visible.forEach { card -> addCardView(card) }
    }

    private fun addCardView(card: Card) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.WHITE, 20f, Color.TRANSPARENT)
            elevation = dp(2).toFloat()
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val network = TextView(this).apply {
            text = card.network
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(35, 39, 52))
        }
        val status = TextView(this).apply {
            text = if (card.used) "مستخدم" else "جاهز للشحن"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (card.used) Color.rgb(100, 104, 115) else Color.rgb(32, 128, 82))
            background = rounded(if (card.used) Color.rgb(239, 240, 244) else Color.rgb(231, 248, 239), 20f, Color.TRANSPARENT)
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        top.addView(network, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(status, lp(-2, dp(32)))
        box.addView(top)

        val number = TextView(this).apply {
            text = formatNumber(card.number)
            textSize = 21f
            typeface = Typeface.create("monospace", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(45, 49, 62))
            background = rounded(Color.rgb(247, 248, 251), 14f, Color.TRANSPARENT)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        box.addView(number, lp(-1, dp(52)).apply { setMargins(0, dp(10), 0, dp(10)) })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val charge = smallButton("اشحن", Color.rgb(68, 82, 210), Color.WHITE) { chargeCard(card) }
        val copy = smallButton("نسخ", Color.rgb(244, 245, 248), Color.rgb(55, 59, 70)) {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("card", card.number))
            Toast.makeText(this, "تم نسخ رقم الكارت", Toast.LENGTH_SHORT).show()
        }
        val toggle = smallButton(if (card.used) "غير مستخدم" else "تم استخدامه", Color.rgb(244, 245, 248), Color.rgb(55, 59, 70)) {
            card.used = !card.used
            saveCards(); renderCards()
        }
        val delete = smallButton("حذف", Color.rgb(255, 242, 242), Color.rgb(190, 55, 55)) { confirmDelete(card) }
        row.addView(charge, weightLp())
        row.addView(copy, weightLp())
        row.addView(toggle, weightLp())
        row.addView(delete, weightLp())
        box.addView(row, lp(-1, dp(48)))
        listContainer.addView(box, lp(-1, -2).apply { setMargins(0, 0, 0, dp(10)) })
    }

    private fun smallButton(textValue: String, bg: Int, fg: Int, click: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 12f
        isAllCaps = false
        setTextColor(fg)
        background = rounded(bg, 13f, Color.TRANSPARENT)
        setOnClickListener { click() }
        stateListAnimator = null
    }

    private fun formatNumber(number: String): String = number.chunked(4).joinToString("  ")

    private fun confirmDelete(card: Card) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("حذف الكارت؟")
            .setMessage("هل تريد حذف كارت ${card.number}؟")
            .setPositiveButton("حذف") { _, _ -> cards.remove(card); saveCards(); renderCards() }
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
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚡ شحن كل الكروت")
            .setMessage("سيتم شحن ${unused.size} كرت بالتتابع، بفاصل 10 ثوانٍ بين كل كرت.\n\nتأكد أن الهاتف يسمح بالمكالمات وأنك تتابع شاشة الاتصال.")
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
        try {
            dialUssd(code)
            card.used = true
            saveCards(); renderCards()
            autoChargeIndex++
            if (autoChargeIndex < autoChargeCards.size) {
                handler.postDelayed({ chargeNextAutomatically() }, AUTO_CHARGE_DELAY_MS)
            } else finishAutoCharge()
        } catch (_: Exception) {
            stopAutoCharge()
            Toast.makeText(this, "تعذر بدء شحن الكارت الحالي", Toast.LENGTH_LONG).show()
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
        autoButton.visibility = View.VISIBLE
        stopButton.visibility = View.GONE
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
                val clean = number.text.toString().filter(Char::isDigit)
                if (clean.isNotBlank()) addCard(spinner.selectedItem.toString(), clean)
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun showExtractDialog() {
        val input = EditText(this).apply {
            hint = "الصق الرسالة هنا…"
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        AlertDialogBuilder(this).setTitle("استخراج رقم من رسالة").setView(input)
            .setPositiveButton("استخراج") { _, _ ->
                val number = extractCardNumber(input.text.toString())
                if (number == null) Toast.makeText(this, "لم يتم العثور على رقم كارت مناسب", Toast.LENGTH_LONG).show()
                else showAddWithNumber(number)
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun showAddWithNumber(number: String) {
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, networks)
        AlertDialogBuilder(this).setTitle("اختار الشبكة").setView(spinner)
            .setPositiveButton("حفظ") { _, _ -> addCard(spinner.selectedItem.toString(), number) }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun extractCardNumber(text: String): String? = Pattern.compile("(?<!\\d)\\d{12,20}(?!\\d)").matcher(text).let { if (it.find()) it.group() else null }

    private fun addCard(network: String, number: String) {
        if (cards.any { it.number == number }) {
            Toast.makeText(this, "الكارت موجود بالفعل", Toast.LENGTH_SHORT).show(); return
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
        dialUssd(code)
        card.used = true
        saveCards(); renderCards()
    }

    private fun dialUssd(code: String) {
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(code)}")))
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر فتح الاتصال", Toast.LENGTH_LONG).show()
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
            pendingCode = null; pendingAutoCharge = false
            Toast.makeText(this, "لا يمكن إجراء المكالمة بدون إذن الاتصال", Toast.LENGTH_LONG).show()
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
                    text = key; textSize = 20f; isAllCaps = false; background = rounded(Color.rgb(246,247,250), 14f, Color.TRANSPARENT)
                    setOnClickListener { display.append(key) }
                }, weightLp())
            }
            layout.addView(row, lp(-1, dp(58)))
        }
        layout.addView(Button(this).apply {
            text = "اتصال"; textSize = 16f; isAllCaps = false; setTextColor(Color.WHITE)
            background = rounded(Color.rgb(68,82,210), 16f, Color.TRANSPARENT)
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

    private fun rounded(color: Int, radiusDp: Float, strokeColor: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp.toInt()).toFloat()
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
