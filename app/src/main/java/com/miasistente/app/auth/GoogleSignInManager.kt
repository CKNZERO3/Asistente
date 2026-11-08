package com.miasistente.app.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * ID de cliente de tipo "Aplicación web" creado en Google Cloud Console
 * (APIs y servicios → Credenciales). Termina en ".apps.googleusercontent.com".
 */
private const val GOOGLE_WEB_CLIENT_ID = "TU_ID_DE_CLIENTE_WEB.apps.googleusercontent.com"

sealed class GoogleSignInResult {
    data class Success(
        val name: String,
        val email: String?,
        val photoUrl: String?
    ) : GoogleSignInResult()

    object Cancelled : GoogleSignInResult()

    data class Error(val message: String) : GoogleSignInResult()
}

class GoogleSignInManager(context: Context) {

    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(activity: Activity): GoogleSignInResult {
        if (GOOGLE_WEB_CLIENT_ID.startsWith("TU_")) {
            return GoogleSignInResult.Error("Falta configurar el ID de cliente web de Google")
        }

        val option = GetSignInWithGoogleOption.Builder(GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleSignInResult.Success(
                    name = google.displayName
                        ?: google.givenName
                        ?: google.id.substringBefore('@'),
                    email = google.id,
                    photoUrl = google.profilePictureUri?.toString()
                )
            } else {
                GoogleSignInResult.Error("Respuesta de Google no reconocida")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleSignInResult.Error("No hay cuentas de Google disponibles en este dispositivo")
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Error("No se pudo iniciar sesión con Google")
        } catch (e: GoogleIdTokenParsingException) {
            GoogleSignInResult.Error("No se pudo leer la respuesta de Google")
        }
    }

    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Si falla, la sesión local se cierra igualmente
        }
    }
}
