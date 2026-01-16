package sk.dilino.fakegps.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

object MockLocationUtils {
    fun checkAndPromptMockLocation(context: Context) {
        val allowMock = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ALLOW_MOCK_LOCATION
        )

        if (allowMock.isNullOrEmpty()) {
            Toast.makeText(
                context,
                "Select this app as Mock Location App",
                Toast.LENGTH_LONG
            ).show()
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            )
        }
    }
}
