package com.example.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = try {
        FirebaseAuth.getInstance().currentUser
    } catch (_: Exception) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun friendlyFirestoreErrorMessage(
    exception: Throwable,
    fallback: String,
    isHindi: Boolean = false
): String {
    val causes = generateSequence(exception) { it.cause }
    val fsErr = causes.firstOrNull { it is FirebaseFirestoreException } as? FirebaseFirestoreException
    return when (fsErr?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            if (isHindi) {
                "इस काम की अनुमति नहीं है। आगे क्या करें: स्कूल मालिक या एडमिन से इस सेक्शन का एक्सेस मांगें।"
            } else {
                "You do not have permission for this action. Next step: ask the school Owner or Admin to enable access for you."
            }
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            if (isHindi) {
                "अभी इंटरनेट बंद है। आपका डेटा फ़ोन में सुरक्षित है — इंटरनेट आते ही अपने आप सिंक हो जाएगा।"
            } else {
                "You are offline right now. Your change is saved on this device and will sync automatically when internet returns."
            }
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            if (isHindi) {
                "नेटवर्क धीमा है। आगे क्या करें: इंटरनेट जांचें और कुछ सेकंड बाद फिर प्रयास करें।"
            } else {
                "The connection took too long. Next step: check your internet signal and tap Retry."
            }
        FirebaseFirestoreException.Code.NOT_FOUND ->
            if (isHindi) {
                "यह रिकॉर्ड नहीं मिला। हो सकता है किसी अन्य सदस्य ने इसे हटा दिया हो — स्क्रीन रिफ्रेश करके जांचें।"
            } else {
                "This record could not be found. Next step: go back and check the updated list."
            }
        FirebaseFirestoreException.Code.ALREADY_EXISTS ->
            if (isHindi) {
                "यह रिकॉर्ड पहले से मौजूद है। आगे क्या करें: सूची में खोजें और उसी रिकॉर्ड को एडिट करें।"
            } else {
                "This record already exists. Next step: search the list and edit the existing entry instead."
            }
        FirebaseFirestoreException.Code.UNAUTHENTICATED ->
            if (isHindi) {
                "आपका लॉगिन सत्र समाप्त हो गया है। आगे क्या करें: दोबारा साइन-इन करें।"
            } else {
                "Your sign-in session expired. Next step: sign in again to continue."
            }
        else -> {
            val raw = fsErr?.localizedMessage ?: exception.localizedMessage
            if (raw.isNullOrBlank()) {
                fallback
            } else if (isHindi) {
                "$fallback ($raw) — कृपया जानकारी जांचकर फिर प्रयास करें।"
            } else {
                "$fallback ($raw). Please review the details and try again."
            }
        }
    }
}
