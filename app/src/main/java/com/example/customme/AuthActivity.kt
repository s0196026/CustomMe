package com.example.customme

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.customme.databinding.ActivityAuthBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var mode = Mode.LOGIN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Если пользователь уже вошёл, сразу открываем приложение.
        if (auth.currentUser != null) {
            openMain()
            return
        }

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        applyMode()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mode == Mode.REGISTER) {
                    mode = Mode.LOGIN
                    applyMode()
                } else {
                    finish()
                }
            }
        })
    }

    private fun setupListeners() {
        binding.btnSubmit.setOnClickListener {
            when (mode) {
                Mode.LOGIN -> login()
                Mode.REGISTER -> register()
            }
        }

        binding.tvSwitchMode.setOnClickListener {
            mode = if (mode == Mode.LOGIN) Mode.REGISTER else Mode.LOGIN
            applyMode()
        }

        binding.etName.doAfterTextChanged { binding.tilName.error = null }
        binding.etEmail.doAfterTextChanged { binding.tilEmail.error = null }
        binding.etPassword.doAfterTextChanged { binding.tilPassword.error = null }
        binding.etConfirmPassword.doAfterTextChanged { binding.tilConfirmPassword.error = null }

        listOf(binding.etName, binding.etEmail, binding.etPassword, binding.etConfirmPassword)
            .forEach { editText ->
                editText.doAfterTextChanged { binding.tvError.isVisible = false }
            }
    }

    private fun applyMode() {
        val isRegister = mode == Mode.REGISTER

        binding.tilName.isVisible = isRegister
        binding.tilConfirmPassword.isVisible = isRegister

        binding.tvFormTitle.text = if (isRegister) "регистрация" else "вход"
        binding.tvFormSubtitle.text = if (isRegister) {
            "создайте аккаунт, чтобы сохранять идеи"
        } else {
            "войдите, чтобы продолжить"
        }
        binding.btnSubmit.text = if (isRegister) "зарегистрироваться" else "войти"
        binding.tvSwitchMode.text = if (isRegister) {
            "уже есть аккаунт? войти"
        } else {
            "нет аккаунта? зарегистрироваться"
        }

        binding.tvError.isVisible = false
        clearErrors()
    }

    private fun login() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        if (!validateEmail(email) || !validatePassword(password)) return

        setLoading(true)

        lifecycleScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                if (!isFinishing) {
                    setLoading(false)
                    openMain()
                }
            } catch (e: Exception) {
                if (!isFinishing) {
                    setLoading(false)
                    showError(authErrorMessage(e))
                }
            }
        }
    }

    private fun register() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()

        if (!validateName(name)) return
        if (!validateEmail(email)) return
        if (!validatePassword(password)) return
        if (!validateConfirmPassword(password, confirmPassword)) return

        setLoading(true)

        lifecycleScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user
                    ?: throw IllegalStateException("Не удалось создать пользователя")

                // Имя храним и в Firebase Auth, и в Firestore.
                try {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                    user.updateProfile(profileUpdates).await()
                } catch (e: Exception) {
                    Log.w(TAG, "Не удалось обновить displayName", e)
                }

                // Если Firestore ещё не настроен, регистрация всё равно не должна падать.
                try {
                    val userData = hashMapOf<String, Any>(
                        "uid" to user.uid,
                        "name" to name,
                        "email" to email,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    firestore.collection(USERS_COLLECTION)
                        .document(user.uid)
                        .set(userData)
                        .await()
                } catch (e: Exception) {
                    Log.w(TAG, "Не удалось сохранить профиль в Firestore", e)
                }

                if (!isFinishing) {
                    setLoading(false)
                    openMain()
                }
            } catch (e: Exception) {
                if (!isFinishing) {
                    setLoading(false)
                    showError(authErrorMessage(e))
                }
            }
        }
    }

    private fun validateName(name: String): Boolean {
        if (name.length < 2) {
            binding.tilName.error = "Введите имя (минимум 2 символа)"
            return false
        }
        binding.tilName.error = null
        return true
    }

    private fun validateEmail(email: String): Boolean {
        if (email.isEmpty()) {
            binding.tilEmail.error = "Введите email"
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Некорректный email"
            return false
        }
        binding.tilEmail.error = null
        return true
    }

    private fun validatePassword(password: String): Boolean {
        if (password.length < 6) {
            binding.tilPassword.error = "Пароль должен быть не короче 6 символов"
            return false
        }
        binding.tilPassword.error = null
        return true
    }

    private fun validateConfirmPassword(password: String, confirmPassword: String): Boolean {
        if (password != confirmPassword) {
            binding.tilConfirmPassword.error = "Пароли не совпадают"
            return false
        }
        binding.tilConfirmPassword.error = null
        return true
    }

    private fun clearErrors() {
        binding.tilName.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.isVisible = loading

        binding.btnSubmit.isEnabled = !loading
        binding.tvSwitchMode.isEnabled = !loading

        binding.tilName.isEnabled = !loading
        binding.tilEmail.isEnabled = !loading
        binding.tilPassword.isEnabled = !loading
        binding.tilConfirmPassword.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.isVisible = true
    }

    private fun authErrorMessage(error: Exception): String {
        val code = (error as? FirebaseAuthException)?.errorCode
        Log.e(TAG, "Firebase auth error: $code", error)

        return when (code) {
            "ERROR_INVALID_EMAIL" -> "Некорректный email"
            "ERROR_USER_NOT_FOUND" -> "Пользователь с таким email не найден"
            "ERROR_WRONG_PASSWORD" -> "Неверный пароль"
            "ERROR_INVALID_CREDENTIAL" -> "Неверный email или пароль"
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Этот email уже зарегистрирован"
            "ERROR_WEAK_PASSWORD" -> "Слишком простой пароль"
            "CONFIGURATION_NOT_FOUND" -> "В Firebase не включён вход по email и паролю"
            "ERROR_OPERATION_NOT_ALLOWED" -> "Вход по email и паролю не включён в Firebase"
            "ERROR_INVALID_API_KEY" -> "Неверная конфигурация Firebase"
            "ERROR_NETWORK_REQUEST_FAILED" -> "Нет подключения к интернету"
            "ERROR_TOO_MANY_REQUESTS" -> "Слишком много попыток. Попробуйте позже"
            else -> error.localizedMessage ?: "Не удалось выполнить действие"
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private enum class Mode {
        LOGIN,
        REGISTER
    }

    private companion object {
        const val TAG = "AuthActivity"
        const val USERS_COLLECTION = "users"
    }
}
