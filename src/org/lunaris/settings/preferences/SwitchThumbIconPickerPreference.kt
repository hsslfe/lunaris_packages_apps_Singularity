/*
 * SPDX-FileCopyrightText: MistOS
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.settings.preferences

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.settings.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.lunaris.settings.utils.SwitchThumbIconManager
import org.lunaris.settings.utils.SwitchThumbIconManager.Preset

/**
 * Lets the user choose the icon shown inside switches when they are ON, with a live preview.
 *
 * The preview shows an OFF and an ON switch: the OFF one always has the stock icon, the ON one shows
 * the highlighted choice. Picking an option applies it system wide right away; the upload button
 * adds the user's own image as the "Custom" option.
 */
class SwitchThumbIconPickerPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
) : Preference(context, attrs, defStyleAttr) {

    /** Called when the user wants to upload their own image; the host fragment opens the picker. */
    var onUploadRequested: (() -> Unit)? = null

    private class Option(val key: String, val label: String)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var executor: ExecutorService = Executors.newSingleThreadExecutor()

    private val options = ArrayList<Option>()
    // Icon bitmaps by option key; the default option has none and shows the stock check mark.
    private val icons = HashMap<String, Bitmap?>()
    private var selectedKey = SwitchThumbIconManager.CHOICE_DEFAULT
    private var attached = false
    private var refreshGeneration = 0
    private var selectionGeneration = 0

    private var afterOn: SwitchPreviewView? = null
    private var afterTitle: TextView? = null
    private var optionList: RecyclerView? = null
    private val adapter = OptionAdapter()

    init {
        layoutResource = R.layout.preference_switch_icon_picker
        isSelectable = false
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        // Before: the stock switch. After: the highlighted choice. OFF is the same in both.
        (holder.findViewById(R.id.preview_before_on) as SwitchPreviewView).apply {
            isOn = true
            setOnIcon(null)
        }
        (holder.findViewById(R.id.preview_before_off) as SwitchPreviewView).isOn = false
        (holder.findViewById(R.id.preview_after_off) as SwitchPreviewView).isOn = false
        afterOn = (holder.findViewById(R.id.preview_after_on) as SwitchPreviewView).apply {
            isOn = true
        }
        afterTitle = holder.findViewById(R.id.preview_after_title) as TextView

        optionList = (holder.findViewById(R.id.switch_icon_options) as RecyclerView).apply {
            layoutManager = GridLayoutManager(context, GRID_COLUMNS)
            adapter = this@SwitchThumbIconPickerPreference.adapter
            itemAnimator = null
        }
        holder.findViewById(R.id.switch_icon_upload)?.setOnClickListener {
            onUploadRequested?.invoke()
        }

        updatePreview()
        refresh()
    }

    override fun onAttached() {
        super.onAttached()
        attached = true
        if (executor.isShutdown) executor = Executors.newSingleThreadExecutor()
    }

    override fun onDetached() {
        attached = false
        refreshGeneration++
        selectionGeneration++
        executor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        optionList?.adapter = null
        optionList = null
        afterOn = null
        afterTitle = null
        super.onDetached()
    }

    /** Re-reads the saved choice and the icons from disk, e.g. after a new image was uploaded. */
    fun refresh() {
        if (!attached) return
        val request = ++refreshGeneration
        val ctx = context
        executor.execute {
            val choice = SwitchThumbIconManager.getChoice(ctx)
            val loaded = HashMap<String, Bitmap?>()
            val list = ArrayList<Option>()

            list.add(Option(SwitchThumbIconManager.CHOICE_DEFAULT,
                    ctx.getString(R.string.switch_icon_default)))
            for (preset in Preset.entries) {
                list.add(Option(preset.key, ctx.getString(preset.label)))
                loaded[preset.key] = SwitchThumbIconManager.loadPreset(ctx, preset)
            }
            if (SwitchThumbIconManager.hasCustom()) {
                list.add(Option(SwitchThumbIconManager.CHOICE_CUSTOM,
                        ctx.getString(R.string.switch_icon_custom)))
                loaded[SwitchThumbIconManager.CHOICE_CUSTOM] = SwitchThumbIconManager.loadCustom()
            }

            mainHandler.post {
                if (!attached || request != refreshGeneration) return@post
                options.clear()
                options.addAll(list)
                icons.clear()
                icons.putAll(loaded)
                selectedKey = choice
                adapter.notifyDataSetChanged()
                updatePreview()
            }
        }
    }

    private fun select(key: String) {
        if (!attached || key == selectedKey) return
        refreshGeneration++
        val request = ++selectionGeneration
        selectedKey = key
        adapter.notifyDataSetChanged()
        updatePreview()

        val ctx = context
        SwitchThumbIconManager.executeChange {
            val ok = runCatching {
                when (key) {
                    SwitchThumbIconManager.CHOICE_DEFAULT -> SwitchThumbIconManager.applyDefault(ctx)
                    SwitchThumbIconManager.CHOICE_CUSTOM -> SwitchThumbIconManager.applyCustom(ctx)
                    else -> Preset.fromKey(key)?.let {
                        SwitchThumbIconManager.applyPreset(ctx, it)
                    } ?: false
                }
            }.getOrDefault(false)
            val savedChoice = SwitchThumbIconManager.getChoice(ctx)
            mainHandler.post {
                if (!attached || request != selectionGeneration) return@post
                selectedKey = savedChoice
                adapter.notifyDataSetChanged()
                updatePreview()
                if (!ok) {
                    Toast.makeText(ctx, R.string.switch_icon_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updatePreview() {
        val icon = icons[selectedKey]
        // The default has no bitmap: the preview then falls back to the stock check mark.
        afterOn?.setOnIcon(icon)
        afterTitle?.text = context.getString(R.string.switch_icon_preview_after,
                options.firstOrNull { it.key == selectedKey }?.label
                        ?: context.getString(R.string.switch_icon_default))
    }

    private companion object {
        const val GRID_COLUMNS = 3
    }

    private inner class OptionAdapter : RecyclerView.Adapter<OptionAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_switch_icon_option, parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val option = options[position]
            val bitmap = icons[option.key]

            if (bitmap != null) {
                holder.icon.setImageBitmap(bitmap)
            } else {
                // Default option: the stock check mark.
                holder.icon.setImageResource(R.drawable.ic_switch_icon_check)
            }
            val selected = option.key == selectedKey
            holder.label.text = option.label
            // The tile and the icon copy this state, which drives the selected look.
            holder.itemView.isActivated = selected
            holder.itemView.isSelected = selected
            holder.itemView.isFocusable = true
            holder.badge.visibility = if (selected) View.VISIBLE else View.GONE
            holder.itemView.contentDescription = option.label
            holder.itemView.setOnClickListener { select(option.key) }
        }

        override fun getItemCount(): Int = options.size

        inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val badge: View = itemView.findViewById(R.id.option_badge)
            val icon: ImageView = itemView.findViewById(R.id.option_icon)
            val label: TextView = itemView.findViewById(R.id.option_label)
        }
    }
}
