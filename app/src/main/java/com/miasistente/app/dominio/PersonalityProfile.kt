// AvatarRepositorio.kt
import android.content.Context
import android.net.Uri
        import androidx.core.net.toUri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AvatarRepositorio(context: Context) {
    private val _customAvatarUri = MutableStateFlow<Uri?>(null)
    val customAvatarUri: Flow<Uri?> = _customAvatarUri.asStateFlow()

    fun updateCustomAvatar(uri: Uri?) {
        _customAvatarUri.value = uri
    }
}
