package com.example.customme

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.customme.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Без авторизации показываем экран входа.
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        // Подключаем ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // По умолчанию открываем Главную
        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }

        // Обработка нажатий на кнопки внизу
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (!programmaticTabChange) {
                openRootScreen(item.itemId)
            }
            true
        }

        // Повторное нажатие на активную вкладку возвращает к её корневому экрану.
        binding.bottomNav.setOnItemReselectedListener { item ->
            if (!programmaticTabChange) {
                openRootScreen(item.itemId)
            }
        }
    }

    /** true, пока вкладка переключается программно (тогда экран меняем сами). */
    private var programmaticTabChange = false

    /** Открывает корневой экран вкладки. */
    private fun openRootScreen(itemId: Int) {
        when (itemId) {
            R.id.nav_home -> replaceFragment(HomeFragment())
            R.id.nav_create -> replaceFragment(CreateFragment())
            R.id.nav_profile -> replaceFragment(ProfileFragment())
        }
    }

    /**
     * Программно переключает вкладку и сразу открывает её корневой экран,
     * не полагаясь на то, что BottomNavigationView вызовет listener.
     */
    private fun selectTab(itemId: Int) {
        programmaticTabChange = true
        binding.bottomNav.selectedItemId = itemId
        binding.bottomNav.menu.findItem(itemId)?.isChecked = true
        programmaticTabChange = false

        openRootScreen(itemId)
    }

    // Вспомогательная функция для смены экранов
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    // Открыть вложенный экран (детали публикации, редактирование) с возвратом назад.
    fun openFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)

        if (addToBackStack) {
            transaction.addToBackStack(null)
        }

        transaction.commit()
    }

    fun showHomeTab() {
        selectTab(R.id.nav_home)
    }

    // Переход в профиль пользователя (например, после публикации или редактирования).
    fun showProfileTab() {
        selectTab(R.id.nav_profile)
    }
}