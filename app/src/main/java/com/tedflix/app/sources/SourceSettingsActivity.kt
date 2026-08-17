package com.tedflix.app.sources

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SourceSettingsActivity : Activity() {
    private lateinit var preferences: SourcePreferences
    private lateinit var sourceList: LinearLayout
    private lateinit var modeDescription: TextView
    private lateinit var summary: TextView
    private lateinit var testButton: Button
    private val sourceRows = linkedMapOf<String, SourceRow>()
    private val executor: ExecutorService = Executors.newFixedThreadPool(4)
    private var selection: SourcePreferences.Selection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        preferences = SourcePreferences(this)
        selection = preferences.read()
        setContentView(buildContent())
        renderSelection()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 8, 12))
        }

        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(12), dp(18), dp(12))
        }
        val back = Button(this).apply {
            text = "‹"
            textSize = 32f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
            contentDescription = "Voltar"
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(52), dp(52)))
        toolbar.addView(TextView(this).apply {
            text = "Fontes de conteúdo"
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(toolbar)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(24))
        }
        content.addView(TextView(this).apply {
            text = "Escolha de onde o Tedflix deve carregar fontes alternativas. A opção principal mantém o servidor atual; a opção Todas consulta os providers disponíveis e informa quais responderam."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(18))
        })

        val modes = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setBackground(roundRect(Color.rgb(20, 22, 30), dp(14)))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val primary = modeRadio("Servidor Tedflix", "Catálogo e player do seu servidor", SourceSelectionMode.PRIMARY)
        val single = modeRadio("Uma fonte alternativa", "Usar um provider escolhido abaixo", SourceSelectionMode.SINGLE)
        val all = modeRadio("Todas as fontes", "Consultar todas as fontes cadastradas", SourceSelectionMode.ALL)
        modes.addView(primary)
        modes.addView(single)
        modes.addView(all)
        modes.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                primary.id -> SourceSelectionMode.PRIMARY
                single.id -> SourceSelectionMode.SINGLE
                else -> SourceSelectionMode.ALL
            }
            val currentSource = selection?.sourceId ?: SourceRegistry.PRIMARY_ID
            val nextSource = if (mode == SourceSelectionMode.PRIMARY) SourceRegistry.PRIMARY_ID else currentSource
            selection = SourcePreferences.Selection(mode, nextSource)
            preferences.save(selection!!)
            modeDescription.text = descriptionFor(mode)
            updateRowsEnabled(mode)
        }
        content.addView(modes, LinearLayout.LayoutParams(-1, -2))

        modeDescription = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(175, 180, 195))
            setPadding(dp(4), dp(10), dp(4), dp(14))
        }
        content.addView(modeDescription)

        content.addView(TextView(this).apply {
            text = "Fontes cadastradas"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(10))
        })

        sourceList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        SourceRegistry.definitions.forEach { definition ->
            val row = createSourceRow(definition)
            sourceRows[definition.id] = row
            sourceList.addView(row.root)
        }

        testButton = Button(this).apply {
            text = "Testar fontes agora"
            setAllCaps(false)
            setOnClickListener { testSources() }
        }
        content.addView(testButton, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(14)
        })

        summary = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(dp(4), dp(12), dp(4), 0)
        }
        content.addView(summary)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun modeRadio(title: String, detail: String, mode: SourceSelectionMode): RadioButton {
        return RadioButton(this).apply {
            id = View.generateViewId()
            text = "$title\n$detail"
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(4), dp(8), dp(4), dp(8))
            isChecked = selection?.mode == mode
            tag = mode
        }
    }

    private fun createSourceRow(definition: SourceDefinition): SourceRow {
        val radio = RadioButton(this).apply {
            id = View.generateViewId()
            isChecked = selection?.sourceId == definition.id
            contentDescription = "Selecionar ${definition.name}"
        }
        val title = TextView(this).apply {
            text = definition.name
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val details = TextView(this).apply {
            text = "${definition.description}\n${definition.baseUrl}"
            textSize = 12f
            setTextColor(Color.rgb(170, 175, 188))
            setPadding(0, dp(2), 0, 0)
        }
        val status = TextView(this).apply {
            text = "Não testada"
            textSize = 12f
            setTextColor(Color.rgb(170, 175, 188))
            setPadding(0, dp(6), 0, 0)
        }
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(details)
            addView(status)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundRect(Color.rgb(18, 20, 27), dp(12))
            setOnClickListener { radio.performClick() }
        }
        root.addView(radio, LinearLayout.LayoutParams(dp(48), -2))
        root.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
        radio.setOnClickListener {
            val current = selection ?: SourcePreferences.Selection(SourceSelectionMode.SINGLE, definition.id)
            val mode = if (current.mode == SourceSelectionMode.PRIMARY) SourceSelectionMode.SINGLE else current.mode
            selection = SourcePreferences.Selection(mode, definition.id)
            preferences.save(selection!!)
            renderSelection()
        }
        return SourceRow(definition.id, root, radio, status)
    }

    private fun renderSelection() {
        val current = selection ?: preferences.read()
        selection = current
        modeDescription.text = descriptionFor(current.mode)
        sourceRows.forEach { (id, row) ->
            row.radio.isChecked = current.sourceId == id
        }
        updateRowsEnabled(current.mode)
        summary.text = when (current.mode) {
            SourceSelectionMode.PRIMARY -> "Fonte ativa: Servidor Tedflix"
            SourceSelectionMode.SINGLE -> "Fonte ativa: ${SourceRegistry.find(current.sourceId)?.name ?: "não selecionada"}"
            SourceSelectionMode.ALL -> "Fonte ativa: todas as fontes cadastradas"
        }
    }

    private fun updateRowsEnabled(mode: SourceSelectionMode) {
        val enabled = mode != SourceSelectionMode.PRIMARY && mode != SourceSelectionMode.ALL
        sourceRows.values.forEach { row ->
            row.radio.isEnabled = enabled
            row.root.alpha = if (enabled) 1f else 0.62f
        }
    }

    private fun descriptionFor(mode: SourceSelectionMode): String = when (mode) {
        SourceSelectionMode.PRIMARY -> "O Tedflix continuará usando o servidor principal e o player HLS já testado."
        SourceSelectionMode.SINGLE -> "Somente o provider selecionado será usado quando essa tela nativa consultar uma fonte."
        SourceSelectionMode.ALL -> "O app poderá consultar todos os providers cadastrados e mostrar o estado de cada um. Fontes fora do ar serão ignoradas."
    }

    private fun testSources() {
        testButton.isEnabled = false
        summary.text = "Testando fontes..."
        SourceRegistry.definitions.forEach { definition ->
            sourceRows[definition.id]?.let { row ->
                row.status.text = "Testando..."
                row.status.setTextColor(Color.rgb(255, 200, 80))
            }
        }
        val sources = SourceRegistry.definitions
        sources.forEach { definition ->
            executor.execute {
                val result = SourceProbe.check(definition)
                runOnUiThread { showResult(result) }
            }
        }
        executor.execute {
            runCatching { Thread.sleep(8_500) }
            runOnUiThread { testButton.isEnabled = true }
        }
    }

    private fun showResult(result: SourceProbeResult) {
        val row = sourceRows[result.sourceId] ?: return
        val code = result.httpCode?.let { " HTTP $it" }.orEmpty()
        row.status.text = when (result.state) {
            SourceProbeState.ONLINE -> "Online$code · ${result.elapsedMs ?: 0} ms"
            SourceProbeState.CHECKING -> "Testando..."
            SourceProbeState.INVALID -> "URL inválida · ${result.message}"
            SourceProbeState.OFFLINE -> "Indisponível$code · ${result.message}"
            SourceProbeState.UNKNOWN -> "Não testada"
        }
        row.status.setTextColor(when (result.state) {
            SourceProbeState.ONLINE -> Color.rgb(120, 220, 140)
            SourceProbeState.INVALID, SourceProbeState.OFFLINE -> Color.rgb(255, 130, 130)
            else -> Color.LTGRAY
        })
        val online = sourceRows.values.count { it.status.text.toString().startsWith("Online") }
        val done = sourceRows.values.count { !it.status.text.toString().startsWith("Testando") }
        summary.text = "Disponíveis: $online de ${sourceRows.size} · concluídas: $done de ${sourceRows.size}"
    }

    private fun roundRect(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1), Color.rgb(38, 41, 52))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private data class SourceRow(
        val id: String,
        val root: LinearLayout,
        val radio: RadioButton,
        val status: TextView,
    )
}
