package com.example.customme

import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object FirebaseErrorMapper {

    fun message(error: Throwable): String {
        return when (error) {
            is FirebaseFirestoreException -> when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Нет доступа к базе данных. Проверьте правила Firestore"
                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "База данных недоступна. Проверьте интернет"
                FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                    "Сессия истекла. Войдите заново"
                else -> error.localizedMessage ?: "Ошибка базы данных"
            }

            is CloudinaryException ->
                "Не удалось загрузить изображение: ${error.message}"

            is UnknownHostException, is ConnectException, is SocketTimeoutException ->
                "Нет соединения с сервером изображений. Проверьте интернет"

            is FileNotFoundException ->
                "Не удалось открыть выбранное изображение. Выберите его заново"

            is SecurityException ->
                "Нет доступа к выбранному изображению. Выберите его заново"

            else -> error.localizedMessage ?: "Не удалось выполнить действие"
        }
    }
}
