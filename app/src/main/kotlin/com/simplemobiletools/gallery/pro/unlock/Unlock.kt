package com.simplemobiletools.gallery.pro.unlock

/**
 * Created by acorn on 2026/2/21.
 */
import android.R.attr.textSize
import android.app.Activity
import android.content.Context
import android.os.CountDownTimer
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.gallery.pro.helpers.Config
import java.security.MessageDigest

// 解锁状态内存存储（每次应用启动后需重新输入）
object UnlockState {
    var isExcludedUnlocked = false
}

// 工具函数：计算字符串的SHA-256哈希（十六进制）
fun String.sha256(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(this.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * 显示设置密码弹窗（用于首次设置或修改密码）
 * @param context 上下文
 * @param onSuccess 设置成功后的回调（可选）
 */
fun showSetPasswordDialog(context: Context, onSuccess: (() -> Unit)? = null) {
    val config = Config.newInstance(context)
    val currentPasswordHash = config.excludedPasswordHash  // 当前存储的密码哈希

    // 创建输入布局
    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 24, 48, 24)
    }

    // 原密码输入框（如果已有密码则必须输入正确，否则留空视为有效）
    val oldPasswordEditText = EditText(context).apply {
        hint = if (currentPasswordHash.isEmpty()) "原密码（无密码时留空）" else "原密码"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 24 }
    }
    layout.addView(oldPasswordEditText)

    // 新密码输入框
    val newPasswordEditText = EditText(context).apply {
        hint = "新密码"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 24 }
    }
    layout.addView(newPasswordEditText)

    // 确认新密码输入框
    val confirmPasswordEditText = EditText(context).apply {
        hint = "确认新密码"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
    layout.addView(confirmPasswordEditText)

    AlertDialog.Builder(context)
        .setTitle("设置密码")
        .setView(layout)
        .setPositiveButton("确定", null) // 先设为null，后续重写点击事件以防止自动关闭
        .setNegativeButton("取消", null)
        .create()
        .apply {
            setOnShowListener {
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val oldPassword = oldPasswordEditText.text.toString()
                    val newPassword = newPasswordEditText.text.toString()
                    val confirmPassword = confirmPasswordEditText.text.toString()

                    // 验证原密码
                    val oldHash = oldPassword.sha256()
                    val isValidOld = when {
                        currentPasswordHash.isEmpty() -> oldPassword.isEmpty()  // 当前无密码时，原密码必须为空
                        else -> oldHash == currentPasswordHash                 // 有密码时，哈希必须匹配
                    }
                    if (!isValidOld) {
                        oldPasswordEditText.error = "原密码错误"
                        return@setOnClickListener
                    }

                    // 验证新密码
                    if (newPassword.isEmpty()) {
                        newPasswordEditText.error = "新密码不能为空"
                        return@setOnClickListener
                    }
                    if (newPassword != confirmPassword) {
                        confirmPasswordEditText.error = "两次输入不一致"
                        return@setOnClickListener
                    }

                    // 保存新密码哈希
                    config.excludedPasswordHash = newPassword.sha256()
                    // 如果存在保护类型，可以设置为密码类型（例如 PROTECTION_PASSWORD）
                    // config.excludedProtectionType = PROTECTION_PASSWORD

                    dismiss()
                    onSuccess?.invoke()
                }
            }
            show()
        }
}

/**
 * 显示输入密码弹窗（用于解锁隐藏功能）
 * @param context 上下文
 * @param onSuccess 解锁成功后的回调（可选）
 */
fun showEnterPasswordDialog(context: Context, onSuccess: (() -> Unit)? = null) {
    val config = Config.newInstance(context)
    val currentPasswordHash = config.excludedPasswordHash

    // 如果没有设置密码，可以直接解锁（或提示先设置密码）
    if (currentPasswordHash.isEmpty()) {
        // 无密码时视为直接可解锁，但可提示用户设置密码
        AlertDialog.Builder(context)
            .setTitle("提示")
            .setMessage("尚未设置密码，是否立即设置？")
            .setPositiveButton("去设置") { _, _ ->
                showSetPasswordDialog(context, onSuccess)
            }
            .setNegativeButton("取消", null)
            .show()
        return
    }

    // 创建输入框
    val passwordEditText = EditText(context).apply {
        hint = "请输入密码"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 24, 48, 24)
        addView(passwordEditText)
    }

    AlertDialog.Builder(context)
        .setTitle("输入密码")
        .setView(layout)
        .setPositiveButton("确定", null)
        .setNegativeButton("取消", null)
        .create()
        .apply {
            setOnShowListener {
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val inputPassword = passwordEditText.text.toString()
                    val inputHash = inputPassword.sha256()

                    if (inputHash == currentPasswordHash) {
                        // 密码正确，更新解锁状态
                        UnlockState.isExcludedUnlocked = true
                        dismiss()
                        onSuccess?.invoke()
                    } else {
                        passwordEditText.error = "密码错误"
                    }
                }
            }
            show()
        }
}

/**
 * 显示重置密码对话框（带倒计时确认）
 * @param context 上下文（应为 Activity）
 * @param onSuccess 重置成功后的回调（可选）
 */
fun showResetPasswordDialog(context: Activity, onSuccess: (() -> Unit)? = null) {
    val config = Config.newInstance(context)
    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 24, 48, 24)
    }

    val warningText = TextView(context).apply {
        text = "警告：此操作将永久删除私有目录中的所有文件（包括所有文件夹和图片），并重置密码。此操作不可撤销。"
        textSize = 16f
        setTextColor(android.graphics.Color.RED)
    }
    layout.addView(warningText)

    val countdownText = TextView(context).apply {
        text = "请等待 10 秒后确认"
        textSize = 14f
        setPadding(0, 16, 0, 0)
    }
    layout.addView(countdownText)

    val dialog = AlertDialog.Builder(context)
        .setTitle("重置密码并删除所有文件")
        .setView(layout)
        .setPositiveButton("确认", null)
        .setNegativeButton("取消", null)
        .create()

    dialog.setOnShowListener {
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton.isEnabled = false

        object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdownText.text = "确认按钮将在 ${millisUntilFinished / 1000} 秒后启用"
            }

            override fun onFinish() {
                countdownText.text = "您已可以确认删除"
                positiveButton.isEnabled = true
            }
        }.start()
    }

    dialog.show()

    // 重设确认按钮点击监听
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        // 立即禁用按钮防止重复点击
        it.isEnabled = false
        ensureBackgroundThread {
            deleteAllPrivateFiles(context)
            // 清空密码哈希
            config.excludedPasswordHash = ""

            context.runOnUiThread {
                context.toast("私有目录已清空，密码已重置")
                // 如果处于私有模式，退出私有模式
                if (UnlockState.isExcludedUnlocked) {
                    UnlockState.isExcludedUnlocked = false
                }
                onSuccess?.invoke()
                dialog.dismiss()
            }
        }
    }
}

/**
 * 递归删除私有目录下的所有文件和文件夹
 */
private fun deleteAllPrivateFiles(context: Context) {
    val privateDir = context.filesDir
    privateDir.listFiles()?.forEach { file ->
        file.deleteRecursively()
    }
}
