package com.grumm.litelauncher

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.EditText

/** Поле поиска: «Назад» закрывает список сразу, а не только прячет клавиатуру. */
class SearchField(context: Context, attrs: AttributeSet?) : EditText(context, attrs) {

    var onBack: (() -> Unit)? = null

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK) return false
        if (event.action == KeyEvent.ACTION_UP) onBack?.invoke()
        return true
    }
}
