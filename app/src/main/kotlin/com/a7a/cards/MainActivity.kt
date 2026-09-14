package com.a7a.cards

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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

    data class Card(
        val id: String,
        val network: String,
        val number: String,
        var used: Boolean
    )

    private val prefs by lazy { getSharedPreferences("cards", MODE_PRIVATE) }
    private val cards = mutableListOf<Card>()
    private lateinit var listContainer: LinearLayout
    private lateinit var searchInput: EditText

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
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(0xFFF7F7FA.toInt())
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 28f
            setTextColor(0xFF111111.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(title, lp(-1, -2))

        val subtitle = TextView(this).apply {
            text = getString(R.string.subtitle)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(12))
        }
        root.addView(subtitle, lp(-1, -2))

        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val addButton = Button(this).apply {
            text = getString(R.string.add_card)
            setOnClickListener { showAddDialog() }
        }
        val pasteButton = Button(this).apply {
            text = getString(R.string.extract_message)
            setOnClickListener { showExtractDialog() }
        }
        addRow.addView(addButton, weightLp())
        addRow.addView(pasteButton, weightLp())
        root.addView(addRow, lp(-1, -2))

        val dialButton = Button(this).apply {
            text = getString(R.string.dial_pad)
            setOnClickListener { showDialPad() }
        }
        root.addView(dialButton, lp(-1, -2))

        searchInput = EditText(this).apply {
            hint = getString(R.string.search_hint)
            singleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        searchInput.addTextChangedListener(SimpleTextWatcher { renderCards() })
        root.addView(searchInput, lp(-1, dp(52)))

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val scroll = ScrollView(this).apply {
            addView(listContainer, lp(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        renderCards()
    }

    private fun renderCards() {
        if (!::listContainer.isInitialized) return
        listContainer.removeAllViews()
        val q = searchInput.text.toString().trim()
        cards.filter { q.isBlank() || it.number.contains(q) || it.network.contains(q, true) }
            .forEach { card ->
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    setBackgroundColor(0xFFFFFFFF.toInt())
                }
                val line1 = TextView(this).apply {
                    text = "${card.network}  •  ${if (card.used) getString(R.string.used) else getString(R.string.unused)}"
                    textSize = 18f
                }
                val line2 = TextView(this).apply {
                    text = card.number
                    textSize = 17f
                    setPadding(0, dp(6), 0, dp(8))
                }
                box.addView(line1)
                box.addView(line2)

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                }
                val charge = Button(this).apply {
                    text = getString(R.string.charge)
                    setOnClickListener { chargeCard(card) }
                }
                val copy = Button(this).apply {
                    text = getString(R.string.copy)
                    setOnClickListener {
                        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("card", card.number))
                        Toast.makeText(this@MainActivity, R.string.copied, Toast.LENGTH_SHORT).show()
                    }
                }
                val delete = Button(this).apply {
                    text = getString(R.string.delete)
                    setOnClickListener { cards.remove(card); saveCards(); renderCards() }
                }
                val usedBtn = Button(this).apply {
                    text = if (card.used) getString(R.string.mark_unused) else getString(R.string.mark_used)
                    setOnClickListener { card.used = !card.used; saveCards(); renderCards() }
                }
                listOf(charge, copy, usedBtn, delete).forEach { row.addView(it, weightLp()) }
                box.addView(row)
                listContainer.addView(box, lp(-1, -2).apply { setMargins(0, dp(8), 0, 0) })
            }
    }

    private fun showAddDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, networks)
        val number = EditText(this).apply {
            hint = getString(R.string.card_number)
            inputType = InputType.TYPE_CLASS_PHONE
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        layout.addView(spinner)
        layout.addView(number, lp(-1, dp(56)))

        AlertDialogBuilder(this)
            .setTitle(R.string.add_card)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val clean = number.text.toString().filter(Char::isDigit)
                if (clean.isNotBlank()) addCard(spinner.selectedItem.toString(), clean)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showExtractDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.message_hint)
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        AlertDialogBuilder(this)
            .setTitle(R.string.extract_message)
            .setView(input)
            .setPositiveButton(R.string.extract) { _, _ ->
                val number = extractCardNumber(input.text.toString())
                if (number == null) {
                    Toast.makeText(this, R.string.no_card_found, Toast.LENGTH_LONG).show()
                } else {
                    showAddWithNumber(number)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddWithNumber(number: String) {
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, networks)
        AlertDialogBuilder(this)
            .setTitle(R.string.choose_network)
            .setView(spinner)
            .setPositiveButton(R.string.save) { _, _ -> addCard(spinner.selectedItem.toString(), number) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun extractCardNumber(text: String): String? {
        val candidates = Pattern.compile("""(?<!\d)\d{12,20}(?!\d)""").matcher(text)
        return if (candidates.find()) candidates.group() else null
    }

    private fun addCard(network: String, number: String) {
        if (cards.any { it.number == number }) {
            Toast.makeText(this, R.string.already_exists, Toast.LENGTH_SHORT).show()
            return
        }
        cards.add(Card(UUID.randomUUID().toString(), network, number, false))
        saveCards()
        renderCards()
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
        saveCards()
        renderCards()
    }

    private var pendingCode: String? = null
    private fun dialUssd(code: String) {
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(code)}")))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.call_failed, Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == REQUEST_CALL && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            pendingCode?.let { dialUssd(it) }
            pendingCode = null
        }
    }

    private fun showDialPad() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val display = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            textSize = 22f
            gravity = Gravity.CENTER
            isSingleLine = true
        }
        layout.addView(display, lp(-1, dp(60)))
        val keys = arrayOf(arrayOf("1","2","3"), arrayOf("4","5","6"), arrayOf("7","8","9"), arrayOf("*","0","#"))
        keys.forEach { rowKeys ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowKeys.forEach { key ->
                row.addView(Button(this).apply {
                    text = key
                    textSize = 20f
                    setOnClickListener { display.append(key) }
                }, weightLp())
            }
            layout.addView(row, lp(-1, dp(58)))
        }
        layout.addView(Button(this).apply {
            text = getString(R.string.call)
            setOnClickListener {
                val code = display.text.toString()
                if (code.isNotBlank()) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                        pendingCode = code
                        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL)
                    } else dialUssd(code)
                }
            }
        }, lp(-1, dp(58)))

        AlertDialogBuilder(this).setTitle(R.string.dial_pad).setView(layout)
            .setNegativeButton(R.string.close, null).show()
    }

    private fun saveCards() {
        val data = cards.joinToString("\n") {
            listOf(it.id, it.network, it.number, it.used).joinToString("|")
        }
        prefs.edit().putString("data", data).apply()
    }

    private fun loadCards() {
        cards.clear()
        prefs.getString("data", "")?.lines()?.filter { it.isNotBlank() }?.forEach { line ->
            val p = line.split("|")
            if (p.size == 4) cards.add(Card(p[0], p[1], p[2], p[3].toBoolean()))
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private fun weightLp() = LinearLayout.LayoutParams(0, dp(52), 1f)

    companion object { const val REQUEST_CALL = 501 }
}

private class SimpleTextWatcher(val action: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { action() }
    override fun afterTextChanged(s: android.text.Editable?) {}
}

private fun AlertDialogBuilder(context: android.content.Context): androidx.appcompat.app.AlertDialog.Builder =
    androidx.appcompat.app.AlertDialog.Builder(context)
