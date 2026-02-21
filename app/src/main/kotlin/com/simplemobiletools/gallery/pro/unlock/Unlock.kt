package com.simplemobiletools.gallery.pro.unlock

/**
 * Created by acorn on 2026/2/21.
 */
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
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
